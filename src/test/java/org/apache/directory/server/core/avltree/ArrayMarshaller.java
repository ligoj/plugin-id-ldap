/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */
package org.apache.directory.server.core.avltree;

import org.apache.directory.shared.ldap.util.StringTools;

import java.io.*;
import java.util.Comparator;

/**
 * Class to serialize the Array data. Only there for Java11 compliance.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
@SuppressWarnings("unchecked")
public class ArrayMarshaller<E> implements Marshaller<ArrayTree<E>> {
	/** used for serialized form of an empty AvlTree */
	private static final byte[] EMPTY_TREE = new byte[1];

	/** marshaller to be used for marshalling the keys */
	private final Marshaller<E> keyMarshaller;

	/** key Comparator for the AvlTree */
	private final Comparator<E> comparator;

	/**
	 * Creates a new instance of AvlTreeMarshaller with a custom key Marshaller.
	 *
	 * @param comparator    Comparator to be used for key comparison
	 * @param keyMarshaller marshaller for keys
	 */
	public ArrayMarshaller(Comparator<E> comparator, Marshaller<E> keyMarshaller) {
		this.comparator = comparator;
		this.keyMarshaller = keyMarshaller;
	}

	/**
	 * Creates a new instance of AvlTreeMarshaller with the default key Marshaller which uses Java Serialization.
	 *
	 * @param comparator Comparator to be used for key comparison
	 */
	public ArrayMarshaller(Comparator<E> comparator) {
		this.comparator = comparator;
		this.keyMarshaller = DefaultMarshaller.INSTANCE;
	}

	@Override
	public byte[] serialize(ArrayTree<E> tree) {
		if (tree == null || tree.isEmpty()) {
			return EMPTY_TREE;
		}

		ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(byteStream);
		byte[] data = null;

		try {
			out.writeByte(0); // represents the start of an Array byte stream
			out.writeInt(tree.size());

			for (int position = 0; position < tree.size(); position++) {
				E value = tree.get(position);
				byte[] bytes = keyMarshaller.serialize(value);

				// Write the key length
				out.writeInt(bytes.length);

				// Write the key if its length is not null
				if (bytes.length != 0) {
					out.write(bytes);
				}
			}

			out.flush();
			data = byteStream.toByteArray();

			// Try to deserialize, just to see
			try {
				deserialize(data);
			} catch (NullPointerException npe) {
				System.out.println("Bad serialization, tree : [" + StringTools.dumpBytes(data) + "]");
				throw npe;
			}

			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return data;
	}

	@Override
	public ArrayTree<E> deserialize(byte[] data) throws IOException {
		try {
			if ((data == null) || (data.length == 0)) {
				throw new IOException("Null or empty data array is invalid.");
			}

			if ((data.length == 1) && (data[0] == 0)) {
				E[] array = (E[]) new Object[] {};
				return new ArrayTree<>(comparator, array);
			}

			ByteArrayInputStream bin = new ByteArrayInputStream(data);
			DataInputStream din = new DataInputStream(bin);

			byte startByte = din.readByte();

			if (startByte != 0) {
				throw new IOException("wrong array serialized data format");
			}

			int size = din.readInt();
			E[] nodes = (E[]) new Object[size];

			for (int i = 0; i < size; i++) {
				// Read the object's size
				int dataSize = din.readInt();

				if (dataSize != 0) {
					byte[] bytes = new byte[dataSize];

					din.read(bytes);
					E key = keyMarshaller.deserialize(bytes);
					nodes[i] = key;
				}
			}

			return new ArrayTree<>(comparator, nodes);
		} catch (NullPointerException npe) {
			System.out.println("Bad tree : [" + StringTools.dumpBytes(data) + "]");
			throw npe;
		}
	}
}
