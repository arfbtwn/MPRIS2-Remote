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

package little.nj.mpris;

import little.nj.CommonComponents.ClientCommand;
import little.nj.CommonComponents.ServerResponse;
import little.nj.ServerPlayerWrapper;
import org.freedesktop.DBus;
import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.DBusSigHandler;
import org.freedesktop.dbus.UInt32;
import org.freedesktop.dbus.exceptions.DBusException;

import java.util.*;

import static little.nj.CommonComponents.EMPTY;
import static little.nj.CommonComponents.PlayerInfo;
import static org.freedesktop.DBus.NameOwnerChanged;
import static org.freedesktop.DBus.Properties.PropertiesChanged;

public class PlayerManager
{
    private static final String OBJECT_DBUS = "/org/freedesktop/DBus";

    public static interface Listener extends EventListener
    {
        void handle ( Event e );
    }

    public static class Event extends EventObject
    {
        public final PlayerInfo[] info;

        public Event ( Object src, PlayerInfo... info )
        {
            super ( src );
            this.info = info;
        }
    }

    private final DBusConnection m_dbus_conn;
    private final DBus m_dbus;
    private final Map < String, ServerPlayerWrapper > m_players;
    private final Map < String, ServerPlayerWrapper > m_owners;
    private final Set < PlayerInfo > m_player_info;

    public PlayerManager ( DBusConnection dbus, ServerPlayerWrapper.BootstrapPlayerInfo... players )
            throws DBusException
    {
        m_dbus_conn = dbus;
        m_dbus = dbus.getRemoteObject ( DBus.class.getCanonicalName (), OBJECT_DBUS, DBus.class );

        m_players = new HashMap <> ();
        m_owners = new HashMap <> ();

        m_player_info = new LinkedHashSet <> ();

        addSignalHandlers ();

        enumeratePlayers ( players );
    }

    private void addSignalHandlers ()
    {
        try
        {
            m_dbus_conn.addSigHandler ( NameOwnerChanged.class, m_name_change );
            m_dbus_conn.addSigHandler ( PropertiesChanged.class, m_properties_changed );
        }
        catch ( Exception e ) { }
    }

    private ServerPlayerWrapper wrap( String name, String service )
    {
        try
        {
            ServerPlayerWrapper player = new ServerPlayerWrapper ( m_dbus_conn, name, service );

            m_players.put ( player.busname (), player );
            m_player_info.add ( player.info ().refresh () );

            if (m_dbus.NameHasOwner ( player.busname () ))
            {
                m_owners.put ( m_dbus.GetNameOwner ( player.busname () ), player );
            }

            return player;
        }
        catch (DBusException de)
        {
            de.printStackTrace ();
            return null;
        }
    }

    private void enumeratePlayers (ServerPlayerWrapper.BootstrapPlayerInfo[] players)
    {
        for ( int i = 0, end = players.length; i < end; ++i )
        {
            wrap ( players[ i ].bus, players[ i ].service );
        }

        for ( String name : m_dbus.ListNames () )
        {
            if ( !name.startsWith ( MprisConstants.IFACE_MPRIS2 ))
            {
                continue;
            }

            if ( m_players.containsKey ( name ) )
            {
                continue;
            }

            wrap( name, null );
        }
    }

    public void execute ( ClientCommand comm, ServerResponse resp )
    {
        ServerPlayerWrapper wrap = m_players.get ( comm.args [ 0 ] );
        switch ( comm.action )
        {
            case Launch:
                m_dbus.StartServiceByName ( wrap.service (), new UInt32 ( 1 ) );
                break;
            default:
                wrap.execute ( comm, resp );
        }
    }

    public PlayerInfo[] info ()
    {
        return m_player_info.toArray ( new PlayerInfo[0] );
    }

    public int size ()
    {
        return m_players.size ();
    }

    private DBusSigHandler< NameOwnerChanged > m_name_change =
            new DBusSigHandler< NameOwnerChanged > ()
            {
                @Override
                public void handle ( NameOwnerChanged s )
                {
                    synchronized ( m_players )
                    {
                        if ( !s.name.startsWith ( MprisConstants.IFACE_MPRIS2 ))
                        {
                            return;
                        }

                        ServerPlayerWrapper player = m_players.get ( s.name );

                        try
                        {
                            if ( null == player )
                            {
                                player = wrap ( s.name, null );
                            }

                            if ( null == player )
                            {
                                return;
                            }

                            if ( !EMPTY.equals ( s.new_owner ) )
                            {
                                m_owners.put ( s.new_owner, player );

                                player.init ();
                            }
                            else
                            {
                                m_owners.remove ( s.old_owner );

                                player.info ().clear ();
                            }
                        }
                        catch (DBusException de)
                        {
                            return;
                        }

                        signalListeners ( player );
                    }
                }
            };

    private DBusSigHandler< PropertiesChanged > m_properties_changed =
            new DBusSigHandler< PropertiesChanged > ()
            {
                @Override
                public void handle ( PropertiesChanged s )
                {
                    synchronized ( m_players )
                    {
                        ServerPlayerWrapper player = m_owners.get ( s.getSource () );

                        if ( null == player )
                            return;

                        player.refresh ();

                        signalListeners ( player );
                    }
                }
            };

    protected final synchronized void signalListeners ( ServerPlayerWrapper player )
    {
        Event evt = new Event ( this, player.info ().refresh () );
        for ( Listener i : m_listeners )
        {
            i.handle ( evt );
        }
    }

    public final synchronized void addListener (Listener aListener)
    {
        m_listeners.add ( aListener );
    }

    public final synchronized void removeListener (Listener aListener)
    {
        m_listeners.remove ( aListener );
    }

    private final List < Listener > m_listeners = new ArrayList <> ();
}
