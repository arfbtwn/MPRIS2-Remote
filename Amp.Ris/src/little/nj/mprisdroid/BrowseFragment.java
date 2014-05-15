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

import android.app.FragmentManager;
import android.app.ListFragment;
import android.content.Context;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import little.nj.CommonComponents.*;
import little.nj.mprisdroid.CommonDroid.OnClientCommandListener;
import little.nj.mprisdroid.CommonDroid.OnServerResponseListener;

import java.lang.ref.WeakReference;

public class BrowseFragment extends ListFragment
{
    /**
     * Clients should push the first browse fragment with this
     * identifier to ensure proper operation when the user is
     * finished browsing
     */
    public static final String BROWSE_ROOT = "BrowseRoot";

    /**
     * Bundle/Arguments Extra: The cached {@link little.nj.CommonComponents.DirectoryToken}
     */
    public static final String DIRECTORY = "Directory";

    /**
     * Bundle/Arguments Extra: The player id any eventual Open File command will go to
     */
    public static final String PLAYER_ID = "PlayerID";

    /**
     * Bundle/Arguments Extra: The starting directory, can be null, in which
     * case the server will decide
     */
    public static final String START_DIR = "StartDirectory";

    /**
     * A factory method for creating a browse fragment, with associated player
     * ID and starting directory
     *
     * @param player_id
     * @param start_dir
     * @return
     */
    public static BrowseFragment createBrowseFragment ( String player_id,
                                                        String start_dir )
    {
        BrowseFragment frag = new BrowseFragment ();

        Bundle args = new Bundle ();
        args.putString ( PLAYER_ID, player_id );
        args.putString ( START_DIR, start_dir );

        frag.setArguments ( args );

        return frag;
    }

    // Our cached vital information
    private String m_player_id;
    private String m_start_dir;
    private DirectoryToken m_directory;

    // Someone who will process commands for us
    private WeakReference < OnClientCommandListener > m_command_listener;

    /*
     * A very simple list adapter for displaying one of two icons
     * on an item
     */
    private BrowseAdapter m_adapter;

    /*
     * The logic of this fragment, when we receive a response we either exit
     * or if it's what we were expecting then we'll display it
     */
    private OnServerResponseListener m_response_listener = new OnServerResponseListener ()
    {
        @Override
        public void onServerResponse ( final ServerResponse serverResponse )
        {
            switch ( serverResponse.resp )
            {
                case Directory:
                    if ( serverResponse.valid )
                        break;
                default:
                    /*
                     * Invalid responses from the server
                     */
                    popPastRoot ();
                    return;
            }

            getActivity ().runOnUiThread (
                    new Runnable ()
                    {
                        @Override
                        public void run ()
                        {
                            m_directory = serverResponse.directory;

                            m_adapter = new BrowseAdapter (
                                    getActivity (),
                                    m_directory.children
                            );
                            setListAdapter ( m_adapter );

                        }
                    }
            );
        }
    };

    /**
     * Pops back to the fragment that started us intially
     */
    private void popPastRoot ()
    {
        /*
         * The activity to start the root directory browser should have put us on the
         * back stack with that tag, we'll use it to go back to parent
         */
        getFragmentManager ()
                .popBackStack ( BROWSE_ROOT, FragmentManager.POP_BACK_STACK_INCLUSIVE );
    }

    @Override
    public void onCreate ( Bundle savedInstanceState )
    {
        super.onCreate ( savedInstanceState );
        setHasOptionsMenu ( true );
        onRestoreInstanceState ( savedInstanceState );
    }

    @Override
    public boolean onOptionsItemSelected ( MenuItem item )
    {
        switch ( item.getItemId () )
        {
            case android.R.id.home:
                popPastRoot ();
                return true;
            default:
                return super.onOptionsItemSelected ( item );
        }
    }

    @Override
    public void onActivityCreated ( Bundle savedInstanceState )
    {
        super.onActivityCreated ( savedInstanceState );

        onRestoreInstanceState ( savedInstanceState );

        /*
         * A late development, the Activity Listener pattern ;) Assume whatever
         * activity is hosting us also is willing to listen for client commands.
         * If it doesn't, well something is horribly wrong and this will hopefully
         * cause a crash
         */
        setOnClientCommandListener ( (CommonDroid.OnClientCommandListener)getActivity () );

        if ( null == m_start_dir )
        {
            signalClientCommand ( new ClientCommand ( Act.Directory ) );
        }
        else
        {
            signalClientCommand ( new ClientCommand ( Act.Directory, m_start_dir ) );
        }
    }

    @Override
    public void onListItemClick ( ListView l, View v, int position, long id )
    {
        RemoteNode clicked = (RemoteNode) l.getItemAtPosition ( position );

        String item = String.format ( "%s%s%s%s%s",
                m_directory.parent,
                m_directory.sep,
                m_directory.name,
                m_directory.sep,
                clicked.name
        );

        if ( clicked.is_dir )
        {
            BrowseFragment next = createBrowseFragment ( m_player_id, item );
            getFragmentManager ().beginTransaction ()
                                 .replace ( android.R.id.tabcontent, next )
                                 .addToBackStack ( null )
                                 .commit ();
        }
        else
        {
            signalClientCommand ( new ClientCommand ( Act.OpenUri, m_player_id, item ) );
        }
    }

    private void onRestoreInstanceState ( Bundle savedInstanceState )
    {
        if ( null == savedInstanceState )
        {
            savedInstanceState = getArguments ();
        }

        m_directory = (DirectoryToken) savedInstanceState.getSerializable ( DIRECTORY );
        m_start_dir = savedInstanceState.getString ( START_DIR );
        m_player_id = savedInstanceState.getString ( PLAYER_ID );
    }

    @Override
    public void onSaveInstanceState ( Bundle outState )
    {
        super.onSaveInstanceState ( outState );

        outState.putSerializable ( DIRECTORY, m_directory );
        outState.putString ( START_DIR, m_start_dir );
        outState.putString ( PLAYER_ID, m_player_id );
    }

    public void setOnClientCommandListener ( OnClientCommandListener aListener )
    {
        m_command_listener = new WeakReference <> ( aListener );
    }

    private void signalClientCommand ( ClientCommand comm )
    {
        OnClientCommandListener listen = m_command_listener.get ();

        if ( null == listen )
            return;

        listen.onClientCommand ( comm, m_response_listener );
    }

    private class BrowseAdapter extends ArrayAdapter < RemoteNode >
    {

        public BrowseAdapter ( Context context, RemoteNode [] nodes )
        {
            super ( context, android.R.layout.simple_list_item_1, nodes );
        }

        @Override
        public View getView ( int position, View convertView, ViewGroup parent )
        {
            if ( null == convertView )
            {
                convertView = getActivity ()
                        .getLayoutInflater ()
                        .inflate ( R.layout.list_item_remotenode, parent, false );
            }

            RemoteNode node = getItem ( position );

            ImageView iv = (ImageView) convertView.findViewById ( R.id.img );
            iv.setImageResource ( node.is_dir
                            ? R.drawable.ic_action_collection
                            : R.drawable.ic_action_slideshow
            );

            TextView tv = (TextView) convertView.findViewById ( R.id.text );
            tv.setText ( node.name );

            // Ensure scrolling for long names
            tv.setSelected ( true );

            return convertView;
        }
    }
}
