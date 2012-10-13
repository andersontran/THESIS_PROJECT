// Copyright 2007 Google Inc. All Rights Reserved.

package com.google.appinventor.components.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashSet;
import java.util.concurrent.CopyOnWriteArrayList;

import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.PropertyCategory;
import com.google.appinventor.components.annotations.SimpleEvent;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.common.YaVersion;

/**
 * Button with the ability to launch events on initialization, focus
 * change, or a user click.  It is implemented using
 * {@link android.widget.Button}.
 *
 */
@DesignerComponent(version = YaVersion.BUTTON_COMPONENT_VERSION,
    category = ComponentCategory.BASIC,
    description = "Button with the ability to detect clicks.  Many aspects " +
    "of its appearance can be changed, as well as whether it is clickable " +
    "(<code>Enabled</code>), can be changed in the Designer or in the Blocks " +
    "Editor.")
@SimpleObject
public final class Button extends ButtonBase {

	interface ServerListener
	{

		/**
		 * Called when the server is started.
		 * @param server the server that is started 
		 */
		public void onServerStarted(Server server);

		/**
		 * Called when the server is stopped.
		 * @param server the server that is stopped 
		 */
		public void onServerStopped(Server server);
		
		/**
		 * Called when a new client (device) connects to the server.
		 * @param server the server that is started 
		 * @param client the Client object representing the newly connected client
		 */
		public void onClientConnect(Server server, Client client);
		
		/**
		 * Called when a new client (device) disconnects from the server.
		 * @param server the server that is started 
		 * @param client the Client that disconnected
		 */
		public void onClientDisconnect(Server server, Client client);

		/**
		 * Called when data is received from the client.
		 * @param client source client
		 * @param data data
		 */
		public void onReceive(Client client, byte data[]);

	}
	
	class AbstractServerListener implements ServerListener
	{

		public void onServerStarted(Server server)
		{
		}

		public void onServerStopped(Server server)
		{
		}

		public void onClientConnect(Server server, Client client)
		{
		}

		public void onClientDisconnect(Server server, Client client)
		{
		}

		public void onReceive(Client client, byte[] data)
		{
		}

	}
	
	class Client
	{
		
		private Socket socket;
		
		private final Server server;
		
		private final InputStream input;
		private final OutputStream output;
		
		private boolean keepAlive = true;
		
		public Client(Server server, Socket socket) throws IOException
		{
			this.server = server;
			this.socket = socket;
			socket.setKeepAlive(true);
			
			this.input = this.socket.getInputStream();
			this.output = this.socket.getOutputStream();

			startCommunicationThread();
		}	
		
		public void startCommunicationThread()
		{
			(new Thread() {
				public void run()
				{
					while (keepAlive)
					{
						try
						{
							
							// Check for input
							if (input.available()>0)
							{
							
								int bytesRead;
								byte buf[] = new byte[input.available()];
								bytesRead = input.read(buf);
								
								if (bytesRead==-1)
									keepAlive = false;
								else
									server.receive(Client.this, buf);
							}
							
						} catch (IOException e)
						{
							keepAlive = false;
							//Log.d("microbridge", "IOException: " + e);
						}
					}
					
					// Client exited, notify parent server
					server.disconnectClient(Client.this);
				}
			}).start();
		}
		
		public void close()
		{
			keepAlive = false;
			
			// Close the socket, will throw an IOException in the listener thread.
			try
			{
				socket.close();
			} catch (IOException e)
			{
				//Log.e("microbridge", "error while closing socket", e);
			}
		}
		
		public void send(byte[] data) throws IOException
		{
			try {
				output.write(data);
				output.flush();
			} catch (SocketException ex)
			{
				// Broken socket, disconnect
				close();
				server.disconnectClient(this);
			}
		}

		public void send(String command) throws IOException
		{
			send(command.getBytes());
		}

	}
	
	class Server
	{
		
		// Server socket for the TCP connection
		private ServerSocket serverSocket = null;
		
		// TCP port to use
		private final int port;

		// List of connected clients. Concurrency-safe arraylist because Clients can join/leave at any point,
		// which means inserts/removes can occur at any time from different threads.
		private CopyOnWriteArrayList<Client> clients = new CopyOnWriteArrayList<Client>();
		
		// Set of event listeners for this server
		private HashSet<ServerListener> listeners = new HashSet<ServerListener>();
		
		// Indicates that the main server loop should keep running. 
		private boolean keepAlive = true;
		
		// Main thread.
		private Thread listenThread;
		
		/**
		 * Constructs a new server instance on port 4567.
		 */
		public Server()
		{
			this(4567);
		}
		
		/**
		 * Constructs a new server instance.
		 * @param port TCP port to use.
		 */
		public Server(int port)
		{
			this.port = port;
		}

		/**
		 * @return TCP port this server accepts connections on.
		 */
		public int getPort()
		{
			return port;
		}

		/**
		 * @return true iff the server is running.
		 */
		public boolean isRunning()
		{
			return listenThread!=null && listenThread.isAlive();
		}
		
		/**
		 * @return the number of currently connected clients
		 */
		public int getClientCount()
		{
			return clients.size();
		}
		
		/**
		 * Starts the server.
		 * @throws IOException
		 */
		public void start() throws IOException
		{
			keepAlive = true;
			serverSocket = new ServerSocket(port);
			
			(listenThread = new Thread(){
				public void run()
				{
					Socket socket;
					try
					{
						while (keepAlive)
						{
							
							try {

								socket = serverSocket.accept();

								// Create Client object.
								Client client = new Client(Server.this, socket);
								clients.add(client);
								
								// Notify listeners.
								for (ServerListener listener : listeners)
									listener.onClientConnect(Server.this, client);
							
							} catch (SocketException ex)
							{
								// A SocketException is thrown when the stop method calls 'close' on the
								// serverSocket object. This means we should break out of the connection
								// accept loop.
								keepAlive = false;
							}
						
						}
						
					} catch (IOException e)
					{
						// TODO
					}
				}
			}).start();
			
			// Notify listeners.
			for (ServerListener listener : listeners)
				listener.onServerStarted(this);
			
		}
		
		/**
		 * Stops the server
		 */
		public void stop()
		{
			// Stop listening in the TCP port.
			if (serverSocket!=null)
				try
				{
					serverSocket.close();
				} catch (IOException e)
				{
					// TODO
				}
				
			// Close all clients.
			for (Client client : clients)
				client.close();
			
			// Notify listeners.
			for (ServerListener listener : listeners)
				listener.onServerStopped(this);
			
		}
		
		/**
		 * Called by the Client class to remove itself from the server. 
		 * 
		 * @param client Client to disconnect
		 */
		protected void disconnectClient(Client client)
		{
			this.clients.remove(client);
			
			for (ServerListener listener : listeners)
				listener.onClientDisconnect(Server.this, client);
		}

		/**
		 * Fires the receive event. Called by the client when it has new data to offer.
		 * 
		 * @param client source client
		 * @param data data 
		 */
		protected void receive(Client client, byte data[])
		{
			// Notify listeners.
			for (ServerListener listener : listeners)
				listener.onReceive(client, data);
		}
		
		/**
		 * Adds a server listener to the server
		 * @param listener a ServerListener instance 
		 */
		public void addListener(ServerListener listener)
		{
			this.listeners.add(listener);
		}
		
		/**
		 * Removes a server listener from the server
		 * @param listener a ServerListener instance 
		 */
		public void removeListener(ServerListener listener)
		{
			this.listeners.remove(listener);
		}
		
		/**
		 * Send bytes to all connected clients.
		 *  
		 * @param data data to send
		 * @throws IOException
		 */
		public void send(byte[] data) throws IOException
		{
			for (Client client : clients)
				client.send(data);
		}

		/**
		 * Send a string to all connected clients
		 * @param str string to send
		 * @throws IOException
		 */
		public void send(String str) throws IOException
		{
			for (Client client : clients)
				client.send(str);
		}
	}
	
	
	// Create TCP server (based on  MicroBridge LightWeight Server). 
		// Note: This Server runs in a separate thread.
		Server server = null;
		
		private int adcSensorValue=10;
		boolean LEDState = false ; //initially OFF
	
  /**
   * Creates a new Button component.
   *
   * @param container container, component will be placed in
   */
  public Button(ComponentContainer container) {
	  
	    super(container);
	    
	  try
		{
			server = new Server(4568); //Use the same port number used in ADK Main Board firmware
			server.start();			
		} catch (IOException e)
		{
			//Log.e("Seeeduino ADK", "Unable to start TCP server", e);
			System.exit(-1);
		}
		
		server.addListener(new AbstractServerListener() {
			 
			@Override
			public void onReceive(Client client, byte[] data)
			{

				if (data.length<2) return;
				adcSensorValue = (data[0] & 0xff) | ((data[1] & 0xff) << 8);


			}

		});
	
  }

 @Override
  public void click() {
    // Call the users Click event handler. Note that we distinguish the click() abstract method
    // implementation from the Click() event handler method.
    Click();
  }

  /**
   * Indicates a user has clicked on the button.
   */
  @SimpleEvent
  public void Click() {
    byte data; 
    
// Toggle the state of LED
if(LEDState == true)
{
	LEDState = false;
	data = 0;
	//bOutPutLED.setText("LED Off");
}
else
{
	LEDState = true;
	data = 1;
	//bOutPutLED.setText("LED On");
}

try
{
	//Send the state of LED to ADK Main Board as a byte
	server.send(new byte[] {(byte) data});
} catch (IOException e)
{
	//Log.e("Seeeduino ADK", "problem sending TCP message", e);
}
	  
    EventDispatcher.dispatchEvent(this, "Click");
  }

  @Override
  public boolean longClick() {
    // Call the users Click event handler. Note that we distinguish the longclick() abstract method
    // implementation from the LongClick() event handler method.
    return LongClick();
  }

  /**
   * Indicates a user has long clicked on the button.
   */
  @SimpleEvent
  public boolean LongClick() {
    return EventDispatcher.dispatchEvent(this, "LongClick");
  }
  
	/**
   * Returns the adcSensorValue.
   *
   * @return  adcSensorValue
   */
  @SimpleProperty(
      category = PropertyCategory.BEHAVIOR)
  public int adcSensorValue() {
    return adcSensorValue;
  }
}
