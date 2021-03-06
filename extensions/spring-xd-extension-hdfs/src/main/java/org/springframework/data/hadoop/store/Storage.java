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

package org.springframework.data.hadoop.store;

import java.io.Closeable;
import java.io.IOException;

import org.apache.hadoop.fs.Path;

import org.springframework.data.hadoop.store.input.InputSplit;

/**
 * Interface for a generic concept of a Storage.
 * 
 * @author Janne Valkealahti
 * 
 */
public interface Storage extends Closeable {

	/**
	 * Gets the storage writer.
	 * 
	 * @return the storage writer
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	StorageWriter getStorageWriter() throws IOException;

	/**
	 * Gets the storage reader.
	 * 
	 * @return the storage reader
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	StorageReader getStorageReader(Path path) throws IOException;

	/**
	 * Gets the storage reader.
	 * 
	 * @param inputSplit the input split
	 * @return the storage reader
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	StorageReader getStorageReader(InputSplit inputSplit) throws IOException;

}
