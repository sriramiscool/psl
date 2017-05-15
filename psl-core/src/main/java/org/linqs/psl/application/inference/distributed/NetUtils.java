/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2015 The Regents of the University of California
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
package org.linqs.psl.application.inference.distributed;

// TODO(eriq): imports
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Some network utilities.
 */
public class NetUtils {
	// Size of an int in bytes,
	public static final int INT_SIZE = Integer.SIZE / Byte.SIZE;

	// A buffer just to read the size of the message into.
	private static ByteBuffer sizeBuffer = ByteBuffer.allocate(INT_SIZE);

	/**
	 * Serialize the task into the buffer and send through the socket.
	 * The buffer is provided only to prevent additional allocation.
	 * The buffer may be reallocated if it is too small (or null).
	 * The used buffer will be returned.
	 */
	public static ByteBuffer sendMessage(Message message, SocketChannel socket, ByteBuffer buffer) {
		buffer = getTransmissionBytes(message, buffer);

		// Send out.
		try {
			while (buffer.hasRemaining()) {
				socket.write(buffer);
			}
		} catch (IOException ex) {
			throw new RuntimeException("Failed to write message.", ex);
		}

		return buffer;
	}

	public static ByteBuffer sendMessage(Message message, OutputStream out, ByteBuffer buffer) {
		buffer = getTransmissionBytes(message, buffer);

		// Send out.
		try {
			out.write(buffer.array());
		} catch (IOException ex) {
			throw new RuntimeException("Failed to write message.", ex);
		}

		return buffer;
	}

	/**
	 * Read a serial task from the socket and put it into the returtned buffer.
	 * Note that we will not actually deserialize the Message.
	 * The buffer is provided only to prevent additional allocation.
	 * The buffer may be reallocated if it is too small (or null).
	 * The used buffer will be returned.
	 */
	public static ByteBuffer readMessage(SocketChannel socket, ByteBuffer buffer) {
		sizeBuffer.clear();

		// Read the size
		try {
			socket.read(sizeBuffer);
		} catch (IOException ex) {
			throw new RuntimeException("Failed to read message size.", ex);
		}

		int payloadSize = sizeBuffer.getInt();

		// Possibly resize the buffer.
		if (buffer == null || buffer.capacity() < payloadSize) {
			buffer = ByteBuffer.allocate(payloadSize);
		}
		buffer.clear();

		// TODO(eriq): Could we read short?
		// Read the full payload.
		try {
			socket.read(buffer);
		} catch (IOException ex) {
			throw new RuntimeException("Failed to read message payload.", ex);
		}

		return buffer;
	}

	public static ByteBuffer readMessage(InputStream inStream, ByteBuffer buffer) {
		sizeBuffer.clear();

		// Read the size
		try {
			inStream.read(sizeBuffer.array());
		} catch (IOException ex) {
			throw new RuntimeException("Failed to read message size.", ex);
		}

		int payloadSize = sizeBuffer.getInt();

		// Possibly resize the buffer.
		if (buffer == null || buffer.capacity() < payloadSize) {
			buffer = ByteBuffer.allocate(payloadSize);
		}
		buffer.clear();

		// TODO(eriq): Could we read short?
		// Read the full payload.
		try {
			inStream.read(buffer.array());
		} catch (IOException ex) {
			throw new RuntimeException("Failed to read message payload.", ex);
		}

		return buffer;
	}

	private static ByteBuffer getTransmissionBytes(Message message, ByteBuffer buffer) {
		byte[] serialMessage = message.serialize();

		int size = serialMessage.length + INT_SIZE;
		if (buffer == null || buffer.capacity() < size) {
			buffer = ByteBuffer.allocate(size);
		}

		buffer.clear();
		buffer.putInt(serialMessage.length);
		buffer.put(serialMessage);

		// Prepare for transfer.
		buffer.flip();
		buffer.compact();

		return buffer;
	}
}
