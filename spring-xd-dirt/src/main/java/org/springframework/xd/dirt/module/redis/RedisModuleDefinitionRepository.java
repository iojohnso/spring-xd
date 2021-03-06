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

package org.springframework.xd.dirt.module.redis;

import java.util.Iterator;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.util.Assert;
import org.springframework.xd.dirt.module.ModuleDefinitionRepository;
import org.springframework.xd.dirt.module.ModuleRegistry;
import org.springframework.xd.module.ModuleDefinition;
import org.springframework.xd.module.ModuleType;
import org.springframework.xd.store.AbstractRedisRepository;

/**
 * An in memory store of {@link ModuleDefinition}s.
 * 
 * @author Mark Fisher
 */
public class RedisModuleDefinitionRepository extends AbstractRedisRepository<ModuleDefinition, String> implements
		ModuleDefinitionRepository {

	private final ModuleRegistry moduleRegistry;

	public RedisModuleDefinitionRepository(String repoPrefix, RedisOperations<String, String> redisOperations,
			ModuleRegistry moduleRegistry) {
		super(repoPrefix, redisOperations);
		Assert.notNull(moduleRegistry, "moduleRegistry must not be null");
		this.moduleRegistry = moduleRegistry;
	}

	@Override
	protected final String keyFor(ModuleDefinition entity) {
		return entity.getType() + ":" + entity.getName();
	}

	@Override
	protected final String serializeId(String id) {
		return id;
	}

	@Override
	protected final String deserializeId(String string) {
		return string;
	}

	@Override
	protected String serialize(ModuleDefinition entity) {
		return entity.getName() + "\n" + entity.getType().name() + "\n" + entity.getDefinition();
	}

	@Override
	protected ModuleDefinition deserialize(String redisKey, String v) {
		String[] parts = v.split("\n");
		ModuleType type = ModuleType.valueOf(parts[1]);
		ModuleDefinition moduleDefinition = new ModuleDefinition(parts[0], type);
		moduleDefinition.setDefinition(parts[2]);
		return moduleDefinition;
	}

	@Override
	public Page<ModuleDefinition> findAll(Pageable pageable) {
		List<ModuleDefinition> definitions = moduleRegistry.findDefinitions();
		Iterator<ModuleDefinition> composed = super.findAll().iterator();
		while (composed.hasNext()) {
			definitions.add(composed.next());
		}
		Assert.isNull(pageable.getSort(), "Arbitrary sorting is not implemented");
		return slice(definitions, pageable);
	}

	@Override
	public List<ModuleDefinition> findByName(String name) {
		Assert.hasText(name, "name is required");
		List<ModuleDefinition> definitions = moduleRegistry.findDefinitions(name);
		Iterator<ModuleDefinition> composed = super.findAll().iterator();
		while (composed.hasNext()) {
			ModuleDefinition next = composed.next();
			if (name.equals(next.getName())) {
				definitions.add(next);
			}
		}
		return definitions;
	}

	@Override
	public ModuleDefinition findByNameAndType(String name, ModuleType type) {
		Assert.notNull(type, "type is required");
		ModuleDefinition definition = moduleRegistry.findDefinition(name, type);
		if (definition == null) {
			definition = super.findOne(type + ":" + name);
		}
		return definition;
	}

	@Override
	public Page<ModuleDefinition> findByType(Pageable pageable, ModuleType type) {
		if (type == null) {
			return findAll(pageable);
		}
		List<ModuleDefinition> definitions = moduleRegistry.findDefinitions(type);
		Iterator<ModuleDefinition> composed = super.findAll().iterator();
		while (composed.hasNext()) {
			ModuleDefinition next = composed.next();
			if (type.equals(next.getType())) {
				definitions.add(next);
			}
		}
		Assert.isNull(pageable.getSort(), "Arbitrary sorting is not implemented");
		return slice(definitions, pageable);
	}

	/**
	 * Post-process the list to only return elements matching the page request.
	 */
	protected Page<ModuleDefinition> slice(List<ModuleDefinition> list, Pageable pageable) {
		int to = Math.min(list.size(), pageable.getOffset() + pageable.getPageSize());
		List<ModuleDefinition> data = list.subList(pageable.getOffset(), to);
		return new PageImpl<ModuleDefinition>(data, pageable, list.size());
	}

}
