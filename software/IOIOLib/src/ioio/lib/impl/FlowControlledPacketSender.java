/*
 * Copyright 2011. All rights reserved.
 *  
 * 
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 * 
 *    1. Redistributions of source code must retain the above copyright notice, this list of
 *       conditions and the following disclaimer.
 * 
 *    2. Redistributions in binary form must reproduce the above copyright notice, this list
 *       of conditions and the following disclaimer in the documentation and/or other materials
 *       provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL ARSHAN POURSOHI OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * 
 * The views and conclusions contained in the software and documentation are those of the
 * authors and should not be interpreted as representing official policies, either expressed
 * or implied.
 */
package ioio.lib.impl;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class FlowControlledPacketSender {
	interface Packet {
		int getSize();
	}
	
	interface Sender {
		void send(Packet packet);
	}

	private Sender sender_;
	private BlockingQueue<Packet> queue_ = new ArrayBlockingQueue<Packet>(
			Constants.PACKET_BUFFER_SIZE);
	FlushThread thread_ = new FlushThread();
	private int readyToSend_ = 0;
	private boolean closed_ = false;

	public FlowControlledPacketSender(Sender sender) {
		sender_ = sender;
		thread_.start();
	}

	synchronized public void flush() throws IOException {
		try {
			while (!queue_.isEmpty()) {
				wait();
			}
		} catch (InterruptedException e) {
		}
	}

	synchronized public void write(Packet packet) {
		if (closed_) {
			throw new IllegalStateException("Stream has been closed");
		}
		queue_.add(packet);
		notifyAll();
	}

	synchronized public void readyToSend(int numBytes) {
		assert(numBytes >= readyToSend_);
		readyToSend_ = numBytes;
		notifyAll();
	}
	
	synchronized public void close() {
		closed_ = true;
		thread_.interrupt();
	}
	
	synchronized public void kill() {
		thread_.interrupt();
	}

	class FlushThread extends Thread {
		@Override
		public void run() {
			super.run();
			try {
				while (true) {
					synchronized (FlowControlledPacketSender.this) {
						while (queue_.isEmpty() || readyToSend_ < queue_.peek().getSize()) {
							FlowControlledPacketSender.this.wait();
						}
						FlowControlledPacketSender.this.notifyAll();
						readyToSend_ -= queue_.peek().getSize();
					}
					sender_.send(queue_.remove());
				}
			} catch (InterruptedException e) {
			}
		}
	}
}