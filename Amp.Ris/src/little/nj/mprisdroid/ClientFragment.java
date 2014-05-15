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

package little.nj.mprisdroid;

import android.app.Fragment;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import little.nj.CommonComponents;
import little.nj.CommonComponents.PlayerInfo;
import little.nj.CommonComponents.ServerResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.net.Socket;
import java.util.*;

import static little.nj.mprisdroid.CommonDroid.OnServerResponseListener;
import static little.nj.mprisdroid.CommonDroid.TAG;

public class ClientFragment extends Fragment
{
    /**
     * Bundle/Arguments Extra: The list of addresses
     */
    public static final String ADDRESSES = "Addresses";

    /**
     * Bundle/Arguments Extra: The address we selected
     */
    public static final String ADDRESS   = "Address";

    /**
     * Bundle/Arguments Extra: The port number to connect with
     */
    public static final String PORT      = "Port";

    /**
     * Factory method for creating a client fragment from the data provided
     * to control players
     *
     * @param addresses An array containing items castable to InetAddress
     * @param port      The port number for the client to connect with
     * @return
     */
    public static ClientFragment create ( Object[] addresses, Integer port )
    {
        Bundle args = new Bundle ();
        args.putSerializable ( ADDRESSES, addresses );
        args.putSerializable ( ADDRESS, null );
        args.putInt ( PORT, port );

        ClientFragment frag = new ClientFragment ();
        frag.setArguments ( args );

        return frag;
    }

    /**
     * Since we drop the connection intentionally during some lifecycle
     * events, we want to try to reconnect before signalling
     * failure to the controlling activity. This interface allows that.
     */
    public static interface OnClientEventListener extends OnServerResponseListener
    {
        void onDisconnect ();
    }

    /*
     * Required state in case we're destroyed
     * FIXME: When we serialize an array of T, we get back an array of Object.
     * http://code.google.com/p/android/issues/detail?id=64583
     */
    private Object[] m_addresses;
    private InetAddress m_addr;
    private Integer m_port;

    /*
     * Connection Machinery
     */
    private final Gson m_gson;
    private Socket   m_sock;
    private OutputStream m_os;
    private InputStream m_is;

    // A thread for listening to the server
    private Thread m_thread;

    // A map of Player ID to PlayerInfo
    // FIXME: This was supposed to be a LinkedHashSet
    private final Map < String, PlayerInfo > m_players;

    /*
     * FIXME: Proper atomic variables
     */
    private volatile Integer transaction_id;
    private volatile Boolean disconnecting;

    /*
     * Map of transaction ID to callback
     */
    private final Map < Integer, WeakReference < OnServerResponseListener > > m_requests;

    /*
     * The activity listening to us will connect here
     */
    private WeakReference < OnClientEventListener > m_listener;

    public ClientFragment ()
    {
        this.m_gson = new GsonBuilder ().create ();

        this.m_players = new LinkedHashMap <> ();

        this.m_requests = new HashMap <> ();

        this.transaction_id = 0;
    }

    @Override
    public void onCreate ( Bundle savedInstanceState )
    {
        super.onCreate ( savedInstanceState );
        setRetainInstance ( true );
        onRestoreInstanceState ( savedInstanceState );
    }

    private void onRestoreInstanceState ( Bundle savedInstanceState )
    {
        if ( null == savedInstanceState )
        {
            savedInstanceState = getArguments ();
        }

        m_addresses = (Object[]) savedInstanceState.getSerializable ( ADDRESSES );
        m_addr = (InetAddress) savedInstanceState.getSerializable ( ADDRESS );
        m_port = savedInstanceState.getInt ( PORT );
    }

    @Override
    public void onSaveInstanceState ( Bundle outState )
    {
        outState.putSerializable ( ADDRESSES, m_addresses );
        outState.putSerializable ( ADDRESS, m_addr );
        outState.putInt ( PORT, m_port );

        super.onSaveInstanceState ( outState );
    }

    @Override
    public void onDestroy ()
    {
        new DisconnectTask ().execute ();

        super.onDestroy ();
    }

    /**
     * Gets the collection of players managed by the fragment
     *
     * @return
     */
    public Collection < PlayerInfo > getPlayers ()
    {
        return m_players.values ();
    }

    /**
     * Checks if the client is connected
     *
     * @return
     */
    public boolean isConnected ()
    {
        return null != m_sock && m_sock.isConnected ();
    }

    /**
     * Sets a listener for handling client events and server responses
     *
     * @param aListener
     */
    public void setOnClientEventListener ( OnClientEventListener aListener )
    {
        this.m_listener = new WeakReference <> ( aListener );
    }

    /**
     * Performs a connection to the server and opens all streams ready for
     * communication
     *
     * @return
     */
    public boolean tryConnect ()
    {
        if ( !openSocket () )
            return false;

        openStreams ();

        return isConnected ();
    }

    /**
     * Opens a socket connection using one of the bundled addresses in the
     * client's argument pack
     *
     * @return true If the socket was opened successfully
     */
    private boolean openSocket ()
    {
        Log.d ( TAG, "ClientFragment: openSocket" );

        if ( isConnected () )
            return true;

        Log.d ( TAG, "ClientFragment: Trying " + m_addresses.length + " Addresses" );
        for ( Object i : m_addresses )
        {
            try
            {
                InetAddress addr = (InetAddress) i;
                String msg = String.format (
                        "Hostname: '%s' Canonical Hostname: '%s' Address: '%s'",
                        addr.getHostName (),
                        addr.getCanonicalHostName (),
                        addr.getHostAddress ()
                );
                Log.d ( TAG, msg );

                m_sock = new Socket ( addr, m_port );

                m_addr = addr;

                break;
            }
            catch ( IOException | ClassCastException e )
            {
                Log.e ( TAG, e.getMessage (), e );
            }
        }

        return isConnected ();
    }

    /**
     * Opens an output and input stream on the socket
     */
    private void openStreams ()
    {
        try
        {
            Log.d ( TAG, "ClientFragment: openStreams" );
            m_os = m_sock.getOutputStream ();
            m_is = m_sock.getInputStream ();

            disconnecting = false;
        }
        catch ( IOException ie )
        {
            Log.e ( TAG, ie.getMessage (), ie );
        }
    }

    /**
     * Disconnects from the server and closes all streams
     *
     * @return
     */
    public void disconnect ()
    {
        if ( null == m_sock )
            return;

        closeSocketAndStreams ();

        return;
    }

    /**
     * Closes the socket and streams
     */
    private void closeSocketAndStreams ()
    {
        try
        {
            Log.d ( TAG, "ClientFragment: closeSocketAndStreams" );

            disconnecting = true;
            m_os.close ();
            m_is.close ();
            m_sock.close ();
        }
        catch ( IOException ie )
        {
            Log.e ( TAG, ie.getMessage (), ie );
        }
        finally
        {
            m_requests.clear ();
            m_sock = null;
            m_is = null;
            m_os = null;
        }
    }

    /**
     * Start the listener thread for server responses
     */
    public void startListening ()
    {
        Log.d ( TAG, "ClientFragment: startListening" );

        m_thread = new Thread (new Runnable () {
            @Override
            public void run ()
            {
                try
                {
                    JsonReader reader = new JsonReader ( new InputStreamReader ( m_is ) );
                    reader.setLenient ( true );

                    do {
                        final ServerResponse resp =
                                m_gson.fromJson ( reader, ServerResponse.class );

                        handleResponse ( resp );

                    } while ( !Thread.interrupted () );
                }
                catch (Exception ie)
                {
                    if ( !disconnecting )
                        Log.e ( TAG, "ClientFragment: Exception while Listening", ie );

                    signalDisconnect ();
                }
            }
        });

        m_thread.start ();
    }

    /**
     * Posts a command to the server
     *
     * @param comm
     */
    public synchronized void handleCommand ( final CommonComponents.ClientCommand comm )
    {
        try
        {
            Log.d ( TAG, "ClientFragment: handleCommand = " + comm.action );
            m_os.write ( m_gson.toJson ( comm ).getBytes () );
        }
        catch ( IOException ie )
        {
            Log.e ( TAG, "ClientFragment: Exception Writing Command to Stream", ie );

            signalDisconnect ();
        }
    }

    /**
     * Posts a command to the server with an asynchronous callback
     *
     * @param comm
     * @param callback
     */
    public synchronized void handleCommand ( final CommonComponents.ClientCommand comm, OnServerResponseListener callback )
    {
        comm.id = transaction_id++;
        m_requests.put ( comm.id, new WeakReference <> ( callback ) );
        handleCommand ( comm );
    }

    /**
     * Handles dispatching server responses to clients
     *
     * @param resp
     */
    private synchronized void handleResponse ( final ServerResponse resp )
    {
        Log.d ( TAG, "ClientFragment: handleResponse" );

        if ( !resp.valid )
        {
            Log.e ( TAG, "ClientFragment: Invalid Response = " + resp.resp );
        }
        else
        {
            // We get first dibs
            switch ( resp.resp )
            {
                case PlayerInfo:
                    List< PlayerInfo > players = Arrays.asList ( resp.players );
                    for ( PlayerInfo i : players )
                    {
                        m_players.put ( i.id, i );
                    }
                    break;
                default:
                    break;
            }
        }

        /*
         * Pass the response to the user's catch all signal
         */
        signalServerResponse ( resp );

        /*
         * Is someone waiting for this to return?
         */
        OnServerResponseListener listen;
        if ( m_requests.containsKey ( resp.id ) && null != ( listen = m_requests.remove ( resp.id ).get () ) )
        {
            listen.onServerResponse ( resp );
        }
    }

    /**
     * Sends the disconnected signal to the primary listener under
     * the appropriate circumstances (i.e. we didn't instigate the
     * disconnection)
     */
    private void signalDisconnect ()
    {
        if ( disconnecting )
            return;

        OnClientEventListener listener = m_listener.get ();

        if ( null != listener )
        {
            listener.onDisconnect ();
        }
    }

    /**
     * Signals server responses to the primary listener
     * @param response
     */
    protected void signalServerResponse (  ServerResponse response )
    {
        OnServerResponseListener listener = m_listener.get ();

        if ( null == listener )
            return;

        listener.onServerResponse ( response );
    }

    /**
     * Network activity can't be on the main thread, this was true for connection
     * and perhaps is true for the disconnect too.
     */
    private class DisconnectTask extends AsyncTask < Void, Void, Void >
    {
        @Override
        protected Void doInBackground ( Void... voids )
        {
            Log.d ( TAG, "ClientFragment:DisconnectTask: doInBackground" );
            disconnect ();
            return null;
        }
    }
}
