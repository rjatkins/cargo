/*
 * ========================================================================
 *
 * Codehaus CARGO, copyright 2004-2011 Vincent Massol, 2012-2020 Ali Tokmen.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * ========================================================================
 */
package org.codehaus.cargo.container.jboss.internal;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;

import org.codehaus.cargo.container.deployable.Deployable;
import org.codehaus.cargo.container.deployable.DeployableType;
import org.codehaus.cargo.container.deployable.WAR;
import org.codehaus.cargo.util.CargoException;
import org.codehaus.cargo.util.FileHandler;
import org.codehaus.cargo.util.log.Logger;

/**
 * Implementation of a Web server that serves one file.
 */
public class SimpleHttpFileServer implements Runnable, ISimpleHttpFileServer
{

    /**
     * Logger instance.
     */
    protected Logger logger;

    /**
     * URL for retrieving file.
     */
    protected URL url;

    /**
     * CARGO file handler.
     */
    protected FileHandler fileHandler;

    /**
     * Path of the file to serve.
     */
    protected String filePath;

    /**
     * Remote path of served file.
     */
    protected String remotePath;

    /**
     * TCP socket.
     */
    protected ServerSocket serverSocket;

    /**
     * Call count.
     */
    protected int callCount;

    /**
     * Has stop been called?
     */
    protected boolean stopped;

    /**
     * Last exception.
     */
    protected Throwable lastException;

    /**
     * create the simple http file server.
     */
    public SimpleHttpFileServer()
    {
        callCount = 0;
    }

    /**
     * @param logger logger to use.
     */
    @Override
    public void setLogger(Logger logger)
    {
        this.logger = logger;
    }

    /**
     * @param handler file handler to use.
     * @param deployable deployable to handle.
     */
    @Override
    public void setFile(FileHandler handler, Deployable deployable)
    {
        String filePath = deployable.getFile();

        this.filePath = filePath;
        this.fileHandler = handler;
        this.remotePath = "/" + getDeployableName(deployable);
    }

    /**
     * @param listenSocket socket to listen on.
     * @param remoteDeployAddress remote hostname to use in the url, if null it will be obtained
     * from the listenSocket.
     */
    @Override
    public void setListeningParameters(InetSocketAddress listenSocket, String remoteDeployAddress)
    {
        if (this.remotePath == null)
        {
            throw new CargoException("Please call setFile first!");
        }

        try
        {
            String finalRemoteDeployAddress = remoteDeployAddress;
            if (finalRemoteDeployAddress == null)
            {
                finalRemoteDeployAddress = listenSocket.getHostName();
            }
            this.url = new URL("http", finalRemoteDeployAddress, listenSocket.getPort(),
                this.remotePath);
        }
        catch (MalformedURLException e)
        {
            throw new CargoException("Could not create a url for " + listenSocket + " and file: "
                + this.remotePath, e);
        }

        try
        {
            this.serverSocket = new ServerSocket(listenSocket.getPort(), 0,
                listenSocket.getAddress());
        }
        catch (IOException e)
        {
            throw new CargoException("Could not create a socket for " + listenSocket, e);
        }
    }

    /**
     * @return url this server serves.
     */
    @Override
    public URL getURL()
    {
        if (this.url == null)
        {
            throw new CargoException("Please call setListeningParameters first!");
        }

        return url;
    }

    /**
     * @return the number of successful calls received.
     */
    @Override
    public int getCallCount()
    {
        return this.callCount;
    }

    /**
     * @return exception, if any occured.
     */
    @Override
    public Throwable getException()
    {
        return this.lastException;
    }

    /**
     * starts the server.
     */
    @Override
    public void start()
    {
        if (this.logger == null)
        {
            throw new CargoException("Please call setLogger first!");
        }

        if (this.serverSocket == null)
        {
            throw new CargoException("Please call setListeningParameters first!");
        }

        this.stopped = false;
        Thread thread = new Thread(this);
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * stops the server.
     */
    @Override
    public void stop()
    {
        this.stopped = true;

        try
        {
            this.serverSocket.close();
        }
        catch (IOException e)
        {
            throw new CargoException("Error stopping embedded HTTP server", e);
        }
    }

    /**
     * runs the thread.
     */
    @Override
    public void run()
    {
        try
        {
            this.runAndThrow();
        }
        catch (Throwable t)
        {
            if (!this.stopped)
            {
                this.lastException = t;
                this.logger.warn("Error in the embedded HTTP server: " + t.toString(),
                    this.getClass().getName());
                for (StackTraceElement ste : t.getStackTrace())
                {
                    this.logger.warn(ste.toString(), this.getClass().getName());
                }
            }
        }
    }

    /**
     * runs the thread.
     * @throws Throwable if anything is thrown.
     */
    private void runAndThrow() throws Throwable
    {
        final String expectedGetRequest = "GET " + this.remotePath;

        while (!this.stopped)
        {
            this.logger.debug("Waiting for connection on socket " + this.serverSocket,
                this.getClass().getName());

            // wait for a connection
            try (Socket socket = this.serverSocket.accept())
            {
                this.logger.debug("Handling request on socket " + socket,
                    this.getClass().getName());

                boolean error = false;
                BufferedReader in = new BufferedReader(new InputStreamReader(
                    socket.getInputStream()));

                String line = in.readLine();
                if (line == null)
                {
                    line = "";
                }
                if (!line.startsWith(expectedGetRequest))
                {
                    error = true;
                }
                while (!"".equals(line))
                {
                    line = in.readLine();
                }

                this.logger.debug("Got HTTP request line " + line, this.getClass().getName());

                OutputStream out = socket.getOutputStream();
                if (error)
                {
                    StringBuilder answer = new StringBuilder();
                    answer.append("HTTP/1.0 404 NOTFOUND");
                    answer.append("\r\n");
                    answer.append("Connection: close");
                    answer.append("\r\n");
                    answer.append("\r\n");

                    out.write(answer.toString().getBytes("US-ASCII"));
                    out.flush();
                }
                else
                {
                    StringBuilder answer = new StringBuilder();
                    answer.append("HTTP/1.0 200 OK");
                    answer.append("\r\n");
                    answer.append("Connection: close");
                    answer.append("\r\n");
                    answer.append("Content-Type: application/octet-stream");
                    answer.append("\r\n");
                    answer.append("Content-Length: ");
                    answer.append(Long.toString(this.fileHandler.getSize(this.filePath)));
                    answer.append("\r\n");
                    answer.append("\r\n");

                    out.write(answer.toString().getBytes("US-ASCII"));
                    out.flush();

                    byte[] fileBytes = new byte[socket.getSendBufferSize()];

                    try (InputStream file = this.fileHandler.getInputStream(this.filePath))
                    {
                        int read;
                        while ((read = file.read(fileBytes)) > 0)
                        {
                            out.write(fileBytes, 0, read);
                            out.flush();
                        }
                    }

                    this.callCount++;
                }

                this.logger.debug("Finished responding to HTTP request line " + line,
                    this.getClass().getName());

                out.flush();
                out.close();
                out = null;
                in.close();
                in = null;
            }
            catch (SocketException ignored)
            {
                // Ignored exception. Not ignoring will break the while loop, end the sending
                // thread and result in the CARGO-859 (JBoss timing out with big files)
            }
        }
    }

    /**
     * Get the deployable name for a given deployable. This also takes into account the WAR context.
     * @param deployable Deployable to get the name for.
     * @return Name for <code>deployable</code>.
     */
    private String getDeployableName(Deployable deployable)
    {
        File localFile = new File(deployable.getFile());
        String localFileName = localFile.getName();
        if (deployable.getType() == DeployableType.WAR)
        {
            WAR war = (WAR) deployable;
            if (war.getContext().isEmpty())
            {
                localFileName = "rootContext.war";
            }
            else
            {
                localFileName = war.getContext() + ".war";
            }
        }

        return localFileName;
    }
}
