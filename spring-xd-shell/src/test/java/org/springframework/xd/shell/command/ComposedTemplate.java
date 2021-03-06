/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.xd.shell.command;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.shell.core.CommandResult;
import org.springframework.shell.core.JLineShellComponent;
import org.springframework.xd.module.ModuleType;
import org.springframework.xd.shell.command.fixtures.Disposable;


/**
 * Issues commands related to module composition and remembers created modules, so that they can be cleanedup.
 * 
 * @author Eric Bottard
 */
public class ComposedTemplate implements Disposable {

	private static final Pattern COMPOSE_SUCCESS_PATTERN = Pattern.compile("Successfully created module '(.+)' with type (.+)");

	private final JLineShellComponent shell;

	public ComposedTemplate(JLineShellComponent shell) {
		this.shell = shell;
	}

	/**
	 * Remembers created modules, in the form <type>:<name>.
	 */
	private List<String> modules = new ArrayList<>();

	public String newModule(String name, String definition) {
		CommandResult result = shell.executeCommand(String.format("module compose %s --definition \"%s\"", name,
				definition));
		Matcher matcher = COMPOSE_SUCCESS_PATTERN.matcher((CharSequence) result.getResult());
		assertTrue("Module composition apparently failed: " + result.getResult(), matcher.matches());
		String key = matcher.group(2) + ":" + matcher.group(1);
		modules.add(key);
		return key;
	}

	public boolean delete(String name, ModuleType type) {
		CommandResult result = shell.executeCommand(String.format("module delete --type %s --name %s", type, name));
		return result.isSuccess();
	}

	@Override
	public void cleanup() {
		Collections.reverse(modules);
		for (String m : modules) {
			String[] parts = m.split(":");
			ModuleType type = ModuleType.valueOf(parts[0]);
			String name = parts[1];
			delete(name, type);
		}
	}

}
