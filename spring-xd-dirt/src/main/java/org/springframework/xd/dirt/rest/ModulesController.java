/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.xd.dirt.rest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.ExposesResourceFor;
import org.springframework.hateoas.PagedResources;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.xd.dirt.module.DependencyException;
import org.springframework.xd.dirt.module.ModuleAlreadyExistsException;
import org.springframework.xd.dirt.module.ModuleDefinitionRepository;
import org.springframework.xd.dirt.module.ModuleDependencyTracker;
import org.springframework.xd.dirt.module.ModuleDeploymentRequest;
import org.springframework.xd.dirt.module.NoSuchModuleException;
import org.springframework.xd.dirt.stream.XDStreamParser;
import org.springframework.xd.module.ModuleDefinition;
import org.springframework.xd.module.ModuleType;
import org.springframework.xd.rest.client.domain.ModuleDefinitionResource;

/**
 * Handles all Module related interactions.
 * 
 * @author Glenn Renfro
 * @author Mark Fisher
 * @author Gunnar Hillert
 * @since 1.0
 */
@Controller
@RequestMapping("/modules")
@ExposesResourceFor(ModuleDefinitionResource.class)
public class ModulesController {

	private final Log logger = LogFactory.getLog(ModulesController.class);

	private final ModuleDefinitionRepository repository;

	private final XDStreamParser parser;

	private ModuleDefinitionResourceAssembler moduleDefinitionResourceAssembler = new ModuleDefinitionResourceAssembler();

	private ModuleDependencyTracker dependencyTracker;

	@Autowired
	public ModulesController(ModuleDefinitionRepository moduleDefinitionRepository,
			ModuleDependencyTracker dependencyTracker) {
		Assert.notNull(moduleDefinitionRepository, "moduleDefinitionRepository must not be null");
		this.repository = moduleDefinitionRepository;
		this.parser = new XDStreamParser(moduleDefinitionRepository);
		this.dependencyTracker = dependencyTracker;
	}

	/**
	 * List Module definitions.
	 */
	@RequestMapping(value = "", method = RequestMethod.GET)
	@ResponseStatus(HttpStatus.OK)
	@ResponseBody
	public PagedResources<ModuleDefinitionResource> list(Pageable pageable,
			PagedResourcesAssembler<ModuleDefinition> assembler,
			@RequestParam(value = "type", required = false) ModuleType type) {
		Page<ModuleDefinition> page = repository.findByType(pageable, type);
		PagedResources<ModuleDefinitionResource> result = safePagedResources(assembler, page);
		return result;
	}

	private PagedResources<ModuleDefinitionResource> safePagedResources(
			PagedResourcesAssembler<ModuleDefinition> assembler,
			Page<ModuleDefinition> page) {
		if (page.hasContent()) {
			return assembler.toResource(page, new ModuleDefinitionResourceAssembler());
		}
		else {
			return new PagedResources<ModuleDefinitionResource>(
					new ArrayList<ModuleDefinitionResource>(), null);
		}
	}

	/**
	 * Create a new composite Module.
	 * 
	 * @param name The name of the module to create (required)
	 * @param definition The module definition, expressed in the XD DSL (required)
	 */
	@RequestMapping(value = "", method = RequestMethod.POST)
	@ResponseStatus(HttpStatus.CREATED)
	@ResponseBody
	public ModuleDefinitionResource save(@RequestParam("name") String name,
			@RequestParam("definition") String definition) {
		List<ModuleDeploymentRequest> modules = this.parser.parse(name, definition);
		ModuleType type = this.determineType(modules);
		if (repository.findByNameAndType(name, type) != null) {
			throw new ModuleAlreadyExistsException(name, type);
		}
		for (ModuleDeploymentRequest child : modules) {
			dependencyTracker.record(child, dependencyKey(name, type));
		}

		ModuleDefinition moduleDefinition = new ModuleDefinition(name, type);
		moduleDefinition.setDefinition(definition);
		ModuleDefinitionResource resource = moduleDefinitionResourceAssembler.toResource(moduleDefinition);
		this.repository.save(moduleDefinition);
		return resource;
	}

	private String dependencyKey(String name, ModuleType type) {
		return String.format("module:%s:%s", type.name(), name);
	}

	/**
	 * Delete a (composite) module.
	 */
	@RequestMapping(value = "/{type}/{name}", method = RequestMethod.DELETE)
	@ResponseStatus(HttpStatus.OK)
	public void delete(@PathVariable("type") ModuleType type, @PathVariable("name") String name) {
		ModuleDefinition definition = repository.findByNameAndType(name, type);
		if (definition == null) {
			throw new NoSuchModuleException(name, type);
		}
		if (definition.getDefinition() == null) {
			throw new IllegalStateException(String.format("Cannot delete non-composed module %s:%s", type, name));
		}
		Set<String> dependedUpon = dependencyTracker.find(name, type);
		if (!dependedUpon.isEmpty()) {
			throw new DependencyException("Cannot delete module %2$s:%1$s because it is used by %3$s", name, type,
					dependedUpon);
		}
		repository.delete(type.name() + ":" + name);
		List<ModuleDeploymentRequest> requests = parser.parse(name, definition.getDefinition());
		for (ModuleDeploymentRequest request : requests) {
			dependencyTracker.remove(request, dependencyKey(name, type));
		}
	}

	private ModuleType determineType(List<ModuleDeploymentRequest> modules) {
		Collections.sort(modules);
		Assert.isTrue(modules != null && modules.size() > 0, "at least one module required");
		if (modules.size() == 1) {
			return modules.get(0).getType();
		}
		ModuleType firstType = modules.get(0).getType();
		ModuleType lastType = modules.get(modules.size() - 1).getType();
		boolean hasInput = firstType != ModuleType.source;
		boolean hasOutput = lastType != ModuleType.sink;
		if (hasInput && hasOutput) {
			return ModuleType.processor;
		}
		if (hasInput) {
			return ModuleType.sink;
		}
		if (hasOutput) {
			return ModuleType.source;
		}
		throw new IllegalArgumentException("invalid module composition; must expose input and/or output channel");
	}

	/**
	 * Retrieve the configuration file for the provided module information.
	 * 
	 * @param name the name of an existing resource (required)
	 * @param type the type of the module (required)
	 */
	@RequestMapping(value = "/{type}/{name}/definition", method = RequestMethod.GET, produces = MediaType.APPLICATION_XML_VALUE)
	@ResponseStatus(HttpStatus.OK)
	@ResponseBody
	public Resource display(@PathVariable("type") ModuleType type, @PathVariable("name") String name) {

		final ModuleDefinition definition = this.repository.findByNameAndType(name, type);

		if (definition == null) {
			throw new NoSuchModuleException(name, type);
		}

		final Resource resource = definition.getResource();

		try {
			if (logger.isWarnEnabled() && resource.getFile().length() == 0) {
				logger.warn(String.format("The length of the file '%s' for module '%s' (%s) is zero.",
						resource.getFilename(), definition.getName(), definition.getType().name()));
			}
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to return the file for the provided resource: "
					+ resource.getFilename(), e);
		}

		return resource;
	}

}
