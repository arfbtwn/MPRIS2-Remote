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

import android.app.ListActivity;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import little.nj.CommonComponents;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;

import static little.nj.CommonComponents.EMPTY;
import static little.nj.mprisdroid.CommonDroid.*;

public class SelectServer extends ListActivity
{
    public static final String SERVICE_TYPE = CommonComponents.DEFAULT_SERVICE_TYPE + ".local.";

    private JmDNS m_dns;
    private ArrayAdapter < ServiceInfo > m_adapter;

    private Button   m_connect;
    private EditText m_address;
    private EditText m_port;

    @Override
    public void onCreate ( Bundle savedInstanceState )
    {
        super.onCreate ( savedInstanceState );
        setContentView ( R.layout.select_server );

        WifiManager wifi = (WifiManager)getSystemService ( WIFI_SERVICE );
        wifi.setWifiEnabled ( true );

        m_adapter = new ServerAdapter ();

        setListAdapter ( m_adapter );

        m_connect = (Button)   findViewById ( R.id.connect );
        m_address = (EditText) findViewById ( R.id.host );
        m_port    = (EditText) findViewById ( R.id.port );

        m_connect.setOnClickListener ( new View.OnClickListener () {
            @Override
            public void onClick ( View view )
            {

                String host = m_address.getText ().toString (),
                       port = m_port.getText ().toString ();

                if ( EMPTY.equals ( host.trim () ) || EMPTY.equals ( port.trim () ) )
                {
                    Toast.makeText (
                            SelectServer.this,
                            R.string.needs_server_port,
                            Toast.LENGTH_SHORT
                    ).show ();

                    return;
                }

                new HostLookupTask ().execute ( m_address.getText ().toString () );
            }
        } );
    }

    @Override
    public void onConfigurationChanged ( Configuration newConfig )
    {
        super.onConfigurationChanged ( newConfig );
    }

    @Override
    public boolean onCreateOptionsMenu ( Menu menu )
    {
        getMenuInflater ().inflate ( R.menu.select_server_bar, menu );
        return super.onCreateOptionsMenu ( menu );
    }

    @Override
    public boolean onOptionsItemSelected ( MenuItem item )
    {
        switch ( item.getItemId () )
        {
            case R.id.about:
                startActivity ( new Intent ( this, AboutActivity.class ) );
                break;
            case R.id.edit:
                View input = findViewById ( R.id.input_server );
                input.setVisibility ( input.getVisibility () ^ View.GONE );
                break;
            case R.id.refresh:
                new RefreshTask ().execute ();
                break;
            default:
                return super.onOptionsItemSelected ( item );
        }

        return true;
    }

    @Override
    protected void onStart ()
    {
        super.onStart ();
    }

    @Override
    protected void onResume ()
    {
        super.onResume ();
        new CreateResponderTask ().execute ();
    }

    @Override
    protected void onPause ()
    {
        new ShutdownResponderTask ().execute ();
        super.onPause ();
    }

    @Override
    public void onListItemClick ( ListView l, View v, int position, long id )
    {
        ServiceInfo info = (ServiceInfo) l.getItemAtPosition ( position );

        Toast.makeText (
                this,
                String.format ( getString( R.string.connect_try ) ,info.getName () ),
                Toast.LENGTH_SHORT
        ).show ();

        startControl ( info.getName (), info.getInetAddresses (), info.getPort () );
    }

    @Override
    protected void onActivityResult ( int requestCode, int resultCode, Intent data )
    {
        switch ( resultCode )
        {
            case CONNECTION_LOST:
                Toast.makeText (
                        this,
                        R.string.lost_connection,
                        Toast.LENGTH_SHORT
                ).show ();
                break;
            case CONNECTION_FAILED:
                Toast.makeText (
                        this,
                        R.string.failed_connection,
                        Toast.LENGTH_SHORT
                ).show ();
                break;
        }

        super.onActivityResult ( requestCode, resultCode, data );
    }

    private void startControl ( String name, InetAddress[] addresses, int port)
    {
        startActivityForResult (
                new Intent ( this, ControlPlayers.class )
                        .putExtra ( ControlPlayers.NAME, name )
                        .putExtra ( ControlPlayers.ADDR, addresses )
                        .putExtra ( ControlPlayers.PORT, port ),
                RESULT_FIRST_USER
        );
    }

    private class CreateResponderTask extends AsyncTask < Void, Void, Void >
    {
        @Override
        protected void onPreExecute ()
        {
            super.onPreExecute ();

            findViewById ( android.R.id.empty ).setVisibility ( View.VISIBLE );
        }

        @Override
        protected Void doInBackground ( Void... voids )
        {
            createResponder ();
            return null;
        }

        @Override
        protected void onPostExecute ( Void aVoid )
        {
            super.onPostExecute ( aVoid );

            warnIfNoMDNS ();
        }
    }

    private void warnIfNoMDNS ()
    {
        if ( null == m_dns )
        {
            Toast.makeText (
                    SelectServer.this,
                    R.string.failed_mdns,
                    Toast.LENGTH_SHORT
            ).show ();

            findViewById ( android.R.id.empty ).setVisibility ( View.GONE );
            findViewById ( R.id.input_server  ).setVisibility ( View.VISIBLE );
        }
    }

    private class ShutdownResponderTask extends AsyncTask < Void, Void, Void >
    {
        @Override
        protected void onPreExecute ()
        {
            super.onPreExecute ();

            findViewById ( android.R.id.empty ).setVisibility ( View.GONE );
        }

        @Override
        protected Void doInBackground ( Void... voids )
        {
            shutdownResponder ();
            return null;
        }

        @Override
        protected void onPostExecute ( Void aVoid )
        {
            super.onPostExecute ( aVoid );

            m_adapter.clear ();
        }
    }

    private void createResponder ()
    {
        Log.d ( TAG, "SelectServer: createResponder" );
        InetAddress addr = null;
        try
        {
            WifiManager wifi = (WifiManager) getSystemService ( WIFI_SERVICE );

            byte[] bytes = BigInteger.valueOf ( wifi.getConnectionInfo ().getIpAddress () ).toByteArray ();

            addr = InetAddress.getByAddress ( bytes );
        }
        catch ( UnknownHostException uhe )
        {
            Log.e ( TAG, uhe.getMessage (), uhe );
        }

        try
        {
            Log.d ( TAG, String.format ( "SelectServer: Listening on %s", addr ) );
            m_dns = null == addr ? JmDNS.create () : JmDNS.create ( addr );
            m_dns.addServiceListener ( SERVICE_TYPE, m_svc_listener );
        }
        catch (Exception e)
        {
            Log.e ( TAG, e.getMessage (), e );
            m_dns = null;
        }
    }

    private void shutdownResponder ()
    {
        try
        {
            Log.d ( TAG, "SelectServer: shutdownResponder" );
            m_dns.removeServiceListener ( SERVICE_TYPE, m_svc_listener );
            m_dns.close ();
        }
        catch ( NullPointerException npe )
        {
            // This is fine, mdns wasn't running
        }
        catch ( IOException ie )
        {
            Log.e ( TAG, "Exception while shutting down mDNS", ie );
        }
        finally
        {
            m_dns = null;
        }
    }

    private class RefreshTask extends AsyncTask < Void, Void, Void >
    {
        @Override
        protected void onPreExecute ()
        {
            super.onPreExecute ();
            m_adapter.clear ();

            findViewById ( R.id.input_server ).setVisibility ( View.GONE );
            findViewById ( android.R.id.empty ).setVisibility ( View.VISIBLE );
        }

        @Override
        protected Void doInBackground ( Void... voids )
        {
            Log.d ( TAG, "SelectServer: Waiting for Responder to Stop..." );
            shutdownResponder ();
            Log.d ( TAG, "SelectServer: Waiting for Responder to Start..." );
            createResponder ();
            return null;
        }

        @Override
        protected void onPostExecute ( Void aVoid )
        {
            super.onPostExecute ( aVoid );

            warnIfNoMDNS ();
        }
    }

    private class HostLookupTask extends AsyncTask < Object, Void, InetAddress >
    {
        @Override
        protected void onPreExecute ()
        {
            super.onPreExecute ();

            findViewById ( android.R.id.empty ).setVisibility ( View.VISIBLE );
        }

        @Override
        protected InetAddress doInBackground ( Object... objects )
        {
            try
            {
                return InetAddress.getByName ( objects [ 0 ].toString () );
            }
            catch (UnknownHostException uhe)
            {
                Log.e ( TAG, uhe.getMessage (), uhe );
                return null;
            }
        }

        @Override
        protected void onPostExecute ( InetAddress inetAddress )
        {
            super.onPostExecute ( inetAddress );

            if ( null == inetAddress )
            {
                Toast.makeText (
                        SelectServer.this,
                        R.string.host_unknown,
                        Toast.LENGTH_SHORT
                ).show ();
                return;
            }

            String port = m_port.getText ().toString ();

            startControl (
                    inetAddress.getHostName (),
                    new InetAddress[] { inetAddress },
                    Integer.parseInt ( port, 10 )
            );
        }
    }

    private ServiceListener m_svc_listener = new ServiceListener ()
    {
        @Override
        public void serviceAdded ( ServiceEvent event )
        {
            Log.d ( TAG, "Service Added: " + event.getInfo ().getName () );
            // We may know the name of the service here, but we don't know it's address
            // or port, etc
            // FIXME: The below statement doesn't work unless we get a new instance of the responder
            m_dns.requestServiceInfo ( event.getType (), event.getName (), 1 );
        }

        @Override
        public void serviceRemoved ( ServiceEvent event )
        {
            Log.d ( TAG, "Service Removed: " + event.getInfo ().getName () );
            final ServiceInfo i = event.getInfo ();

            runOnUiThread (
                    new Runnable ()
                    {
                        @Override
                        public void run ()
                        {
                            m_adapter.remove ( i );
                        }
                    }
            );
        }

        @Override
        public void serviceResolved ( ServiceEvent event )
        {
            Log.d ( TAG, "Service Resolved: " + event.getInfo ().getName () );
            final ServiceInfo i = event.getInfo ();

            runOnUiThread (
                    new Runnable ()
                    {
                        @Override
                        public void run ()
                        {
                            m_adapter.add ( i );
                        }
                    }
            );
        }
    };

    private class ServerAdapter extends ArrayAdapter < ServiceInfo >
    {
        ServerAdapter ()
        {
            super ( SelectServer.this, android.R.layout.simple_list_item_2 );
        }

        @Override
        public View getView ( int position, View convertView, ViewGroup parent )
        {
            ServiceInfo info = getItem ( position );

            View v = getLayoutInflater ().inflate ( android.R.layout.simple_list_item_2, parent, false );

            TextView tv = (TextView) v.findViewById ( android.R.id.text1 );
            tv.setText ( info.getName () );
            tv = (TextView) v.findViewById ( android.R.id.text2 );
            tv.setText ( info.getInetAddresses () [ 0 ].getHostAddress () );

            return v;
        }

        @Override
        public void add ( ServiceInfo object )
        {
            int idx = getPosition ( object );
            switch ( idx )
            {
                case -1:
                    super.add ( object );
                    break;
                default:
                    super.remove ( object );
                    super.insert ( object, idx );
                    break;
            }
        }
    }
}
