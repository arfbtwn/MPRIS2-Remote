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

import android.app.Activity;
import android.app.FragmentManager;
import android.app.ListFragment;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import little.nj.CommonComponents;

import java.io.File;
import java.util.Collection;

import static little.nj.CommonComponents.*;
import static little.nj.mprisdroid.CommonDroid.*;

public class ControlPlayers extends Activity
                            implements OnClientCommandListener,
                                       ClientFragment.OnClientEventListener,
                                       PlayerFragment.OnArtRequestListener
{

    /**
     * Intent Extra: The user visible server name
     */
    public static final String NAME = "Hostname";

    /**
     * Intent Extra: The list of addresses to try
     */
    public static final String ADDR = "InetAddr";

    /**
     * Intent Extra: The port number for the eventual socket
     */
    public static final String PORT = "Port";

    /**
     * The friendly server name
     */
    private String        m_name;

    /**
     * We'll keep the client state here
     * Note: This fragment persists for configuration changes
     */
    private ClientFragment m_data;

    /**
     * Our list and associated adapter
     */
    private ListFragment m_list;
    private PlayerListAdapter m_adapter;


    /**
     * An ad-hoc album art manager
     */
    private ArtCacheHelper m_artman;

    /**
     * The async task we use to connect with
     */
    private ConnectTask m_connect;

    @Override
    public void onCreate ( Bundle savedInstanceState )
    {
        super.onCreate ( savedInstanceState );

        setContentView ( R.layout.player_host );

        Intent in = getIntent ();

        m_name = in.getExtras ().getString ( NAME );

        setTitle ( m_name );

        m_adapter = new PlayerListAdapter ();

        m_list = (ListFragment) getFragmentManager ().findFragmentByTag ( FRAG_LIST );
        m_data = (ClientFragment) getFragmentManager ().findFragmentByTag ( FRAG_DATA );

        m_connect = new ConnectTask ();

        if ( null == m_data )
        {
            Log.d ( TAG, "ControlPlayers: onCreate = Starting Fresh" );
            m_list = new ListFragment ();
            m_data = ClientFragment.create (
                    (Object[]) in.getExtras ().get ( ADDR ),
                    in.getExtras ().getInt ( PORT )
            );

            m_data.setOnClientEventListener ( this );

            m_artman = new ArtCacheHelper ( m_data );

            getFragmentManager ().beginTransaction ()
                                 .add ( m_data, FRAG_DATA )
                                 .add ( android.R.id.tabcontent, m_list, FRAG_LIST )
                                 .commit ();

            m_connect.execute ();
        }
        else
        {
            Log.d ( TAG, "ControlPlayers: onCreate = Loading State" );
            FragmentManager fm = getFragmentManager ();

            m_data.setOnClientEventListener ( this );

            m_artman = new ArtCacheHelper ( m_data );

            Collection<PlayerInfo> players = m_data.getPlayers ();

            PlayerFragment frag;
            for ( PlayerInfo i : players )
            {
                frag = (PlayerFragment) fm.findFragmentByTag ( i.id );

                if ( null == frag )
                    continue;

                frag.init ( i );
            }

            if ( !m_data.isConnected () )
            {
                m_connect.execute ();
            }
            else
            {
                m_list.setListAdapter ( m_adapter );
                m_adapter.addAll ( players );
                m_adapter.notifyDataSetChanged ();
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected ( MenuItem item )
    {
        Log.d ( TAG, "ControlPlayers: onOptionsItemSelected" );
        switch ( item.getItemId () )
        {
            case android.R.id.home:
                m_connect.cancel ( true );
            default:
                return super.onOptionsItemSelected ( item );
        }
    }

    @Override
    public void onBackPressed ()
    {
        Log.d ( TAG, "ControlPlayers: onBackPressed" );
        super.onBackPressed ();

        if ( m_list.isVisible () )
        {
            setTitle ( m_name );
        }
    }

    @Override
    protected void onDestroy ()
    {
        Log.d ( TAG, "ControlPlayers: onDestroy" );

        m_connect.cancel ( true );

        super.onDestroy ();
    }

    @Override
    public boolean onCreateOptionsMenu ( Menu menu )
    {
        super.onCreateOptionsMenu ( menu );
        getActionBar ().setDisplayHomeAsUpEnabled ( true );
        return true;
    }

    @Override
    public void onDisconnect ()
    {
        Log.d ( TAG, "ControlPlayers: onDisconnect" );
        setResult ( CONNECTION_LOST );
        finish ();
    }

    @Override
    public void onServerResponse ( ServerResponse serverResponse )
    {
        Log.d ( TAG, "ControlPlayers: onServerResponse = " + serverResponse.resp.toString () );
        switch ( serverResponse.resp )
        {
            case Na:
            case Deliver:
                runOnUiThread ( new Runnable () {
                                    @Override
                                    public void run ()
                                    {
                                        m_adapter.notifyDataSetChanged ();
                                    }
                                } );
                break;
            case Directory:
            case PlaylistInfo:
                break;
            case PlayerInfo:
                for ( PlayerInfo i : serverResponse.players )
                {
                    onPlayerInfoUpdate ( i );
                }
                break;
        }
    }

    private void onPlayerInfoUpdate ( final PlayerInfo playerInfo )
    {
        runOnUiThread (
                new Runnable ()
                {
                    @Override
                    public void run ()
                    {
                        m_adapter.addOrUpdate ( playerInfo );

                        FragmentManager fm = getFragmentManager ();
                        PlayerFragment frag = (PlayerFragment) fm.findFragmentByTag ( playerInfo.id );

                        if ( null == frag )
                            return;

                        frag.sync ( playerInfo );
                    }
                }
        );
    }

    @Override
    public void onClientCommand ( ClientCommand clientCommand )
    {
        m_data.handleCommand ( clientCommand );
    }

    @Override
    public void onClientCommand ( ClientCommand clientCommand,
                                  OnServerResponseListener callback )
    {
        m_data.handleCommand ( clientCommand, callback );
    }

    @Override
    public void onArtRequest ( Metadata data, ArtCacheHelper.OnArtReceivedListener callback )
    {
        m_artman.requestArt ( data, callback );
    }

    private class ConnectTask extends AsyncTask < Void, Void, Boolean >
    {
        @Override
        protected Boolean doInBackground ( Void... voids )
        {
            Log.d ( TAG, "ControlPlayers:ConnectTask: doInBackground" );

            return m_data.tryConnect ();
        }

        @Override
        protected void onPostExecute ( Boolean result )
        {
            Log.d ( TAG, "ControlPlayers:ConnectTask: onPostExecute" );
            super.onPostExecute ( result );

            if ( !result )
            {
                setResult ( CONNECTION_FAILED );
                finish ();
            }
            else
            {
                m_data.startListening ();
                m_data.handleCommand ( new ClientCommand ( Act.Players ) );
                m_list.setListAdapter ( m_adapter );
            }
        }
    }

    private class PlayerListAdapter extends ArrayAdapter < CommonComponents.PlayerInfo >
    {
        public PlayerListAdapter ()
        {
            super ( ControlPlayers.this, android.R.layout.simple_list_item_1 );
        }

        @Override
        public View getView ( final int position, View convertView, final ViewGroup parent )
        {
            final CommonComponents.PlayerInfo item = getItem ( position );

            if ( convertView == null )
            {
                convertView = getLayoutInflater ().inflate ( R.layout.list_item_player, parent, false );
            }

            TextView tv = (TextView) convertView.findViewById ( R.id.name );
            tv.setText ( EMPTY.equals ( item.name )
                            ? item.id.substring ( item.id.lastIndexOf ( "." ) + 1 )
                            : item.name
            );

            ImageButton ib = (ImageButton) convertView.findViewById ( R.id.play );

            ib.setEnabled ( true );
            switch ( item.state )
            {
                case Inactive:
                    ib.setImageResource ( R.drawable.ic_action_warning );
                    break;
                case Playing:
                    ib.setImageResource ( R.drawable.ic_action_pause );
                    break;
                case Paused:
                case Stopped:
                    ib.setImageResource ( R.drawable.ic_action_play );
                    break;
            }

            ib.setOnClickListener (
                    new View.OnClickListener ()
                    {
                        @Override
                        public void onClick ( View view )
                        {
                            if ( PlayerState.Inactive == item.state )
                            {
                                m_data.handleCommand ( new ClientCommand ( Act.Launch, item.id ) );
                            }
                            else
                            {
                                m_data.handleCommand ( new ClientCommand ( Act.PlayPause, item.id ) );
                            }
                        }
                    }
            );

            // FIXME: This hack should not be required.
            convertView.setOnClickListener (
                    new View.OnClickListener ()
                    {
                        @Override
                        public void onClick ( View view )
                        {
                            if ( PlayerState.Inactive == item.state )
                            {
                                m_data.handleCommand ( new ClientCommand ( Act.Launch, item.id ) );

                                Toast.makeText (
                                        ControlPlayers.this,
                                        R.string.launch_try,
                                        Toast.LENGTH_SHORT
                                ).show ();

                                return;
                            }


                            PlayerFragment frag = new PlayerFragment ();

                            frag.init ( item );

                            getFragmentManager ().beginTransaction ()
                                    .replace ( android.R.id.tabcontent, frag, item.id )
                                    .addToBackStack ( item.id )
                                    .commit ();
                        }
                    }
            );

            final ImageView iv = (ImageView) convertView.findViewById ( R.id.img );

            m_artman.requestArt ( item.metadata, new ArtCacheHelper.OnArtReceivedListener () {
                        @Override
                        public void onArtReceived ( final boolean valid, String remoteUri, final File file )
                        {
                            runOnUiThread (
                                    new Runnable ()
                                    {
                                        @Override
                                        public void run ()
                                        {
                                            if ( valid )
                                            {
                                                iv.setImageBitmap (
                                                        BitmapFactory.decodeFile ( file.getAbsolutePath () )
                                                );
                                                iv.setVisibility ( View.VISIBLE );
                                            }
                                            else
                                            {
                                                iv.setVisibility ( View.GONE );
                                            }
                                        }
                                    }
                            );
                        }
                    } );


            return convertView;
        }

        /**
         * Android lacks a smart data structure we can use here :(
         *
         * @param info The record to update (remove/insert)
         */
        public void addOrUpdate ( PlayerInfo info )
        {
            int idx = getPosition ( info );

            switch ( idx )
            {
                case -1:
                    add ( info );
                    break;
                default:
                    PlayerInfo item = getItem ( idx );

                    if ( info.state != item.state || !info.metadata.url.equals ( item.metadata.url ))
                    {
                        remove ( item );
                        insert ( info, idx );
                    }
                    break;
            }
        }
    }
}
