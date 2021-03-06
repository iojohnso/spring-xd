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

package org.springframework.xd.shell.command.fixtures;

import org.springframework.shell.core.JLineShellComponent;


/**
 * Base class for Metrics related sinks, makes sure named metric is deleted at end of test.
 * 
 * @author Eric Bottard
 */
public class AbstractMetricSink extends AbstractModuleFixture implements
		Disposable {

	final String name;

	final String shellCommand;

	final String dslName;

	final JLineShellComponent shell;


	protected AbstractMetricSink(JLineShellComponent shell, String name, String type) {
		this(shell, name, type, type);
	}

	protected AbstractMetricSink(JLineShellComponent shell, String name, String shellCommand, String dslName) {
		this.shell = shell;
		this.name = name;
		this.shellCommand = shellCommand;
		this.dslName = dslName;
	}

	@Override
	public void cleanup() {
		// Do not fail here if command fails, as this is typically called alongside
		// other cleanup code
		shell.executeCommand(String.format("%s delete --name %s", shellCommand, name));
	}

	@Override
	protected String toDSL() {
		return String.format("%s --name=%s", dslName, name);
	}
}
