/*
 * Copyright (c) 2014. Nicholas Little < arealityfarbetween@googlemail.com >
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

package little.nj;

import avahi4j.Avahi4JConstants;
import avahi4j.Client;
import avahi4j.EntryGroup;
import avahi4j.exceptions.Avahi4JException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import little.nj.CommonComponents.*;
import little.nj.mpris.PlayerManager;
import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import sun.misc.BASE64Encoder;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static little.nj.CommonComponents.*;
import static little.nj.mpris.MprisConstants.*;
import static little.nj.mpris.PlayerManager.Event;
import static little.nj.mpris.PlayerManager.Listener;

public class Daemon implements AutoCloseable, Runnable
{
    /*
     * Our thread names
     */
    public static final String THREAD_COMMAND  = "CommandRunner";
    public static final String THREAD_RESPONSE = "ResponseRunner";
    public static final String THREAD_CLIENT   = "ClientListener";

    /**
     * The default number of execution threads
     */
    public static final int DEFAULT_EXECUTIONS = 2;

    /**
     * The default set of players we'll try to control, totem, banshee
     * and audacious
     */
    public static ServerPlayerWrapper.BootstrapPlayerInfo[] DEFAULT_PLAYERS = {
            new ServerPlayerWrapper.BootstrapPlayerInfo ( IFACE_MPRIS2 + "." + "totem", null ),
            new ServerPlayerWrapper.BootstrapPlayerInfo ( IFACE_MPRIS2 + "." + "banshee", "org.bansheeproject.Banshee" ),
            new ServerPlayerWrapper.BootstrapPlayerInfo ( IFACE_MPRIS2 + "." + "audacious", null )
    };

    // Remoting Machinery
    ServerSocket m_socket_connection;
    DBusConnection m_dbus_connection;
    AvahiHelper m_avahi;
    Gson m_gson;

    // Our Avahi Details
    final String m_server_name;
    final String m_service_type;

    // A couple of thread safe queue runners
    final QueueRunner < TransactionWrapper > m_commands;
    final QueueRunner < TransactionWrapper > m_responses;

    // Our list of clients and players
    final List < Beastie > m_minions;
    final PlayerManager m_players;

    // Our command and response runners
    Thread m_command_runner, m_response_runner;
    Executor m_exection_pool;

    QueueRunner.Action < TransactionWrapper > m_act_command = new QueueRunner.Action< TransactionWrapper > () {
        @Override
        public void act ( final TransactionWrapper x )
        {
            m_exection_pool.execute (
                    new Runnable ()
                    {
                        @Override
                        public void run ()
                        {
                            executeServerCommand ( x );
                            m_responses.offer ( x );
                        }
                    }
            );
        }
    };

    QueueRunner.Action < TransactionWrapper > m_act_response = new QueueRunner.Action< TransactionWrapper > () {
        @Override
        public void act ( TransactionWrapper x )
        {
            executeServerResponse ( x );
        }
    };

    public Daemon ( int threads,
                    String svc_type,
                    String name,
                    int port,
                    ServerPlayerWrapper.BootstrapPlayerInfo[] players )
            throws IOException, DBusException
    {
        m_server_name = name;
        m_service_type = svc_type;

        m_gson = new GsonBuilder ().create ();
        m_commands = new QueueRunner <> ( new ArrayDeque < TransactionWrapper > (), m_act_command );
        m_responses = new QueueRunner <> ( new ArrayDeque < TransactionWrapper > (), m_act_response );
        m_minions = new ArrayList <> ();
        m_exection_pool = Executors.newFixedThreadPool ( threads );

        // Open Port
        m_socket_connection = new ServerSocket ( port );

        // Enumerate Media Players
        m_dbus_connection = DBusConnection.getConnection ( DBusConnection.SESSION );
        m_players = new PlayerManager ( m_dbus_connection, players );
        m_players.addListener ( m_listen_players );
    }

    public Daemon ( String svc_type,
                    String name,
                    Integer port,
                    ServerPlayerWrapper.BootstrapPlayerInfo... players )
            throws IOException, DBusException
    {
        this ( DEFAULT_EXECUTIONS, svc_type, name, port, players );
    }

    public Daemon ( String server_name,
                    Integer port,
                    ServerPlayerWrapper.BootstrapPlayerInfo[] players )
            throws IOException, DBusException
    {
        this ( DEFAULT_EXECUTIONS, DEFAULT_SERVICE_TYPE, server_name, port, players );
    }

    public Daemon ( String server_name, Integer port ) throws IOException, DBusException
    {
        this ( DEFAULT_EXECUTIONS, DEFAULT_SERVICE_TYPE, server_name, port, DEFAULT_PLAYERS );
    }

    public Daemon () throws IOException, DBusException
    {
        this ( DEFAULT_EXECUTIONS, DEFAULT_SERVICE_TYPE, DEFAULT_SERVER_NAME, DEFAULT_PORT, DEFAULT_PLAYERS );
    }

    @Override
    public void run ()
    {
        m_command_runner = new Thread ( m_commands, THREAD_COMMAND );
        m_command_runner.start ();
        m_response_runner = new Thread ( m_responses, THREAD_RESPONSE );
        m_response_runner.start ();

        announceService ();

        waitForConnection ();
    }

    private void announceService ()
    {
        try
        {
            System.out.printf (
                    "Host: '%s' Port: '%d'%n",
                    InetAddress.getLocalHost ().getHostName (),
                    m_socket_connection.getLocalPort ()
            );
        }
        catch ( UnknownHostException uhe )
        {
            System.out.printf (
                    "Port: '%d'%n",
                    m_socket_connection.getLocalPort ()
            );
        }

        m_avahi = new AvahiHelper ();
        m_avahi.announce (
            m_server_name,
            m_service_type,
            m_socket_connection.getLocalPort ()
        );
    }

    /**
     * Executes a server command, issuing any replies to the contained
     * output stream
     *
     * @param comm
     */
    private void executeServerCommand ( TransactionWrapper comm )
    {
        try
        {
            System.out.printf ( "%s: %s%n", comm.cause.action, Arrays.deepToString (comm.cause.args) );

            ClientCommand client = comm.cause;
            ServerResponse resp = comm.effect;
            resp.resp = Response.Na;

            switch ( client.action )
            {
                case Players:
                    resp.resp = Response.PlayerInfo;
                    resp.players = m_players.info ();
                    break;
                case Power:
                    break;
                case Retrieve:
                    resp.resp = Response.Deliver;

                    resp.base64 = new Base64Token ();
                    resp.base64.uri = client.args [ 0 ].toString ();

                    URI uri = new URI ( resp.base64.uri );
                    try ( InputStream is = uri.toURL ().openStream () )
                    {
                        byte[] buff = new byte [ 8192 ];
                        int read;
                        ByteArrayOutputStream bos = new ByteArrayOutputStream ();
                        while ( -1 != (read = is.read ( buff ) ) )
                        {
                            bos.write ( buff, 0, read );
                        }

                        BASE64Encoder enc = new BASE64Encoder ();

                        resp.base64.b64 = enc.encode ( bos.toByteArray () );
                    }
                    break;
                case Directory:
                    resp.resp = Response.Directory;
                    File file = 0 == client.args.length
                            ? new File ( System.getProperty ( "user.home" ) )
                            : new File ( client.args [ 0 ] );

                    File parent = file.getParentFile ();

                    resp.directory = new DirectoryToken ();
                    resp.directory.sep = File.separator;
                    resp.directory.parent = parent == null ? null : parent.getAbsolutePath ();
                    resp.directory.name = file.getName ();
                    List < RemoteNode > children = new ArrayList <> ();
                    for ( File i : file.listFiles () )
                    {
                        if ( !i.getName ().startsWith ( "." ) )
                            children.add (  new RemoteNode ( i.getName (), i.isDirectory () ) );
                    }
                    Collections.sort ( children );
                    resp.directory.children = children.toArray ( new RemoteNode [ children.size () ] );
                    break;
                case Ping:
                    resp.resp = Response.Pong;
                    break;
                default:
                    m_players.execute ( client, resp );
                    break;
            }

            /*
             * Command completed without incident
             */
            resp.valid = true;
        }
        catch (Exception ex) { ex.printStackTrace (); }
    }

    private void executeServerResponse ( TransactionWrapper x )
    {
        x.rsvp.offer ( x.effect );
    }

    /**
     * Blocks for connections, and spawns mini daemons
     */
    private void waitForConnection ()
    {
        do
        {
            try
            {
                Beastie client = new Beastie ( m_socket_connection );

                synchronized ( m_minions )
                {
                    m_minions.add ( client );
                    System.out.println ( "Clients: " + m_minions.size () );
                }
            }
            catch ( IOException ioe )
            {
                // We'll ignore these and go on listening
                ioe.printStackTrace ();
            }

        } while ( !Thread.interrupted () );
    }

    private void requestDisposal ( Beastie client )
    {
        synchronized ( m_minions )
        {
            m_minions.remove ( client );
            System.out.println ( "Clients: " + m_minions.size () );
        }
        try
        {
            client.close ();
        }
        catch ( Exception ex ) { }
    }

    @Override
    public void close () throws Exception
    {
        m_players.removeListener ( m_listen_players );

        m_command_runner.interrupt ();
        m_response_runner.interrupt ();

        m_avahi.close ();

        m_dbus_connection.disconnect ();

        for ( Beastie i : m_minions )
        {
            i.close ();
        }

        m_socket_connection.close ();
    }

    @Override
    public String toString ()
    {
        return String.format ( "%d Players Configured", m_players.size () );
    }

    /**
     * Each client's connection is managed by one of these mini daemons
     */
    protected class Beastie implements AutoCloseable
    {
        final Socket m_sock;

        final OutputStream m_os;
        final InputStream m_is;

        private Thread m_listener;
        private Thread m_responder;
        private QueueRunner < ServerResponse > m_responses;

        private QueueRunner.Action < ServerResponse > m_send = new QueueRunner.Action< ServerResponse> () {
            @Override
            public void act ( ServerResponse effect )
            {
                try
                {
                    m_os.write ( m_gson.toJson ( effect ).getBytes () );
                }
                catch (IOException ie)
                {
                    ie.printStackTrace ();
                }
            }
        };

        public Beastie ( ServerSocket sock ) throws IOException
        {
            m_sock = sock.accept ();

            m_os = m_sock.getOutputStream ();
            m_is = m_sock.getInputStream ();

            respond ();

            listen ();
        }

        private void respond ()
        {
            m_responses = new QueueRunner <> ( new ArrayDeque <ServerResponse> (), m_send );
            m_responder = new Thread ( m_responses, THREAD_RESPONSE );
            m_responder.start ();
        }

        private void listen ()
        {
            m_listener = new Thread (
                    new Runnable ()
                    {
                        @Override
                        public void run ()
                        {
                            try
                            {
                                JsonReader reader = new JsonReader ( new InputStreamReader ( m_is ) );
                                reader.setLenient ( true );

                                do
                                {
                                    ClientCommand c = m_gson.fromJson ( reader, ClientCommand.class );

                                    m_commands.offer ( new TransactionWrapper ( Beastie.this, c ) );

                                } while ( !Thread.interrupted () );
                            }
                            catch (Exception ex) { }
                            finally
                            {
                                /**
                                 * Remove and dispose of inactive connections
                                 */
                                requestDisposal ( Beastie.this );
                            }
                        }
                    }, THREAD_CLIENT
            );

            m_listener.start ();
        }

        public void offer ( ServerResponse serverResponse )
        {
            this.m_responses.offer ( serverResponse );
        }

        @Override
        public void close () throws Exception
        {
            m_listener.interrupt ();
            m_responder.interrupt ();

            m_is.close ();
            m_os.close ();

            m_sock.close ();
        }
    }

    private static class AvahiHelper implements AutoCloseable
    {
        Client m_avahi_client;
        EntryGroup m_avahi_group;

        AvahiHelper ()
        {
            try
            {
                m_avahi_client = new Client ();
                m_avahi_client.start ();

                m_avahi_group = m_avahi_client.createEntryGroup ();
            }
            catch ( Avahi4JException ae ) { }
            catch ( Error err ) { }
        }

        public boolean ready ()
        {
            return null != m_avahi_client && null != m_avahi_group;
        }

        public void announce ( String name, String type, int port )
        {
            if ( !ready () )
            {
                return;
            }

            m_avahi_group.addService (
                    Avahi4JConstants.AnyInterface,
                    Avahi4JConstants.Protocol.ANY,
                    name,
                    type,
                    null, null,
                    port,
                    null
             );

            m_avahi_group.commit ();
        }

        @Override
        public void close () throws Exception
        {
            if ( !ready () )
                return;

            m_avahi_group.release ();
            m_avahi_client.release ();
        }
    }

    private final Listener m_listen_players = new Listener () {
        @Override
        public void handle ( Event e )
        {
            synchronized ( m_minions )
            {
                ServerResponse resp = new ServerResponse ();
                resp.resp = Response.PlayerInfo;
                resp.valid = true;
                resp.players = e.info;

                for ( Beastie i : m_minions )
                {
                    i.offer ( resp );
                }
            }
        }
    };

}
