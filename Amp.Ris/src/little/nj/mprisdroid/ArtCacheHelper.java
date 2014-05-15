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

import android.content.Context;
import android.util.Base64;
import android.util.Log;
import little.nj.CommonComponents.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.EventListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static little.nj.CommonComponents.*;
import static little.nj.mprisdroid.CommonDroid.TAG;

/**
 * Manages the caching and retrieval of Album Art
 */
class ArtCacheHelper implements CommonDroid.OnServerResponseListener
{
    /**
     * An interface for our clients
     */
    public interface OnArtReceivedListener extends EventListener
    {
        void onArtReceived ( boolean valid, String remoteUri, File file );
    }

    /**
     * If we generate the cache file ourselves, any of these characters will
     * be replaced with an underscore
     */
    public static final char[] ILLEGALS = " :;'@~#,()[]<>{}|\\/`¬!\"?£$%^&*".toCharArray ();

    /**
     * The data model - although ClientFragment can give us a reference
     * to the Context, this is safe as it is a retainable fragment
     */
    private final ClientFragment m_data;

    /**
     * A map of uri request to file names we've successfully
     * downloaded or already got from a previous session
     */
    private final HashMap < String, File > m_cached;

    /**
     * The map of files we're waiting for
     */
    private final HashMap < String, File > m_waiting;

    /**
     * A map of listeners
     */
    private final HashMap < String, Set < WeakReference < OnArtReceivedListener > > > m_requests;

    public ArtCacheHelper ( ClientFragment client )
    {
        this.m_data = client;
        this.m_cached = new HashMap <> ();
        this.m_waiting = new HashMap <> ();
        this.m_requests = new HashMap <> ();
    }

    /**
     * Since this component is not a fragment, we need somewhere to access the context
     *
     * This function returns the result of {@link android.app.Fragment#getActivity()}
     * on the encapsulated {@link little.nj.mprisdroid.ClientFragment}
     *
     * @return
     */
    private Context getContext () { return m_data.getActivity (); }

    /**
     * Generates a cache file name based upon the given metadata package
     *
     * @param data
     * @return
     */
    private String getChildName ( Metadata data )
    {
        String child;
        try
        {
            URI uri = new URI ( data.art_url );
            child = new File ( uri.getPath () ).getName ();
        }
        catch ( URISyntaxException use )
        {
            child = String.format (
                    "%s-%s",
                    data.getFirstArtist (),
                    data.album
                    );


            for ( char i : ILLEGALS )
            {
                child = child.replace ( i, '_' );
            }
        }

        return child;

    }

    /**
     * Returns a child File reference of the cache with the specified name
     *
     * @param child
     * @return
     */
    private File mapToCacheFile ( String child )
    {
        return new File ( getContext ().getCacheDir (), child );
    }

    /**
     * Requests art from the cache, with an asynchronous response in cases where
     * it must be retrieved from the server
     *
     * @param data
     * @param artReceivedListener
     */
    public synchronized void requestArt ( Metadata data, OnArtReceivedListener artReceivedListener )
    {
        String child = getChildName ( data );

        if ( EMPTY.equals ( child ) )
        {
            artReceivedListener.onArtReceived ( false, data.art_url, null );
            return;
        }

        if ( m_cached.containsKey ( data.art_url ) )
        {
            File f = m_cached.get ( data.art_url );
            artReceivedListener.onArtReceived ( null != f, data.art_url, f );
            return;
        }

        File local = mapToCacheFile ( child );

        if ( local.exists () )
        {
            m_cached.put ( data.art_url, local );
            artReceivedListener.onArtReceived ( true, data.art_url, m_cached.get ( data.art_url ) );
            return;
        }

        Set < WeakReference < OnArtReceivedListener > > listeners = m_requests.get ( data.art_url );

        if ( null == listeners )
        {
            listeners = new HashSet <> ();

            m_requests.put ( data.art_url, listeners );
            m_waiting.put ( data.art_url, local );

            m_data.handleCommand (
                    new ClientCommand (
                            Act.Retrieve,
                            data.art_url.hashCode (),
                            data.art_url
                    ),
                    this
            );
        }

        listeners.add ( new WeakReference <> ( artReceivedListener ) );
    }

    /**
     * When receiving responses, they are delivered to clients in this function
     *
     * @param serverResponse
     */
    @Override
    public synchronized void onServerResponse ( ServerResponse serverResponse )
    {
        String uri = serverResponse.base64.uri;

        Log.d ( TAG, String.format ( "ArtCacheHelper: { Uri: %s, Valid: %s }", uri, serverResponse.valid ) );

        checkSave ( serverResponse.base64 );

        Set < WeakReference < OnArtReceivedListener > > listeners = m_requests.remove ( uri );

        for ( WeakReference < OnArtReceivedListener > listen : listeners )
        {
            OnArtReceivedListener l = listen.get ();

            if ( null == l )
                continue;

            l.onArtReceived ( serverResponse.valid, uri, m_cached.get ( uri ) );
        }
    }

    /**
     * Performs the necessary actions to record a file we're waiting for as retrieved
     * and save it in the cache for clients
     *
     * @param payload
     */
    private void checkSave ( Base64Token payload )
    {
        if ( m_cached.containsKey ( payload.uri ) )
            return;

        File local = m_waiting.remove ( payload.uri );

        if ( null == payload.b64 )
        {
            m_cached.put ( payload.uri, null );
            return;
        }

        FileOutputStream stream = null;

        try
        {
            Log.d ( TAG, String.format ( "Saving: %s => %s", payload.uri, local.getAbsolutePath () ));
            stream = new FileOutputStream ( local );
            stream.write ( Base64.decode ( payload.b64, Base64.DEFAULT ) );

            m_cached.put ( payload.uri, local );
        }
        catch ( IOException ie )
        {
            Log.e ( TAG, ie.getMessage (), ie );
        }
        finally
        {
            if ( null != stream )
            {
                try
                {
                    stream.close ();
                }
                catch ( IOException ie )
                {
                    // This one we'll just ignore
                }
            }
        }
    }
}
