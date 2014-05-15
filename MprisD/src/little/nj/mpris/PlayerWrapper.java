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

import little.nj.CommonComponents.*;
import little.nj.PropertiesHelper;
import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.Path;
import org.freedesktop.dbus.UInt32;
import org.freedesktop.dbus.Variant;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.mpris.MediaPlayer2;

import java.io.File;
import java.util.*;

import static little.nj.mpris.MprisConstants.*;
import static little.nj.mpris.MprisConstants.Metadata;

public class PlayerWrapper
{
    public static class BootstrapPlayerInfo
    {
        public final String bus;
        public final String service;
        /*
         * We could try to support TrackList, but I'm yet to see it implemented
         */
        public final boolean mp2_playlists;

        public BootstrapPlayerInfo ( String bus, String service, boolean mp2_playlists )
        {
            this.bus = bus;
            this.service = service;

            this.mp2_playlists = mp2_playlists;
        }

        @Override
        public int hashCode ()
        {
            return bus.hashCode ();
        }
    }

    private final DBusConnection m_dbus;

    private final PlayerInfoHelper m_info_helper;

    private final BootstrapPlayerInfo m_bootstrap;

    private MediaPlayer2 m_mp2;
    private MediaPlayer2.Player m_mp2_play;
    private MediaPlayer2.Playlists m_mp2_playlists;

    private final Map < String, PropertiesHelper > m_properties;

    public PlayerWrapper ( DBusConnection dbus, BootstrapPlayerInfo info )
    {
        this.m_dbus = dbus;

        this.m_bootstrap = info;

        this.m_info_helper = new PlayerInfoHelper ();

        this.m_properties = new HashMap <> ();

        init ();
    }

    public void init ()
    {
        try
        {
            getRemotes ();

            refresh ();
        }
        catch (DBusException de)
        {
            de.printStackTrace ();
            m_mp2 = null;
            m_mp2_play = null;
            m_mp2_playlists = null;
        }
    }

    private void getRemotes () throws DBusException
    {
        m_mp2 = m_dbus.getRemoteObject ( busname (), object (), MediaPlayer2.class );
        m_mp2_play = m_dbus.getRemoteObject ( busname (), object (), MediaPlayer2.Player.class );
        m_properties.put (
                IFACE_MPRIS2, new PropertiesHelper (
                        m_dbus,
                        busname (),
                        object (),
                        MediaPlayer2.class.getCanonicalName ()
                )
        );
        m_properties.put (
                IFACE_MPRIS2_PLAYER, new PropertiesHelper (
                        m_dbus,
                        busname (),
                        object (),
                        MediaPlayer2.Player.class.getCanonicalName ()
                )
        );

        if ( m_bootstrap.mp2_playlists )
        {
            m_mp2_playlists = m_dbus.getRemoteObject ( busname (), object (), MediaPlayer2.Playlists.class );
            m_properties.put (
                    IFACE_MPRIS2_PLAYLISTS, new PropertiesHelper (
                            m_dbus,
                            busname (),
                            object (),
                            MediaPlayer2.Playlists.class.getCanonicalName ()
                    )
            );
        }
    }

    public void refresh ()
    {
        try
        {
            for ( PropertiesHelper i : m_properties.values () )
            {
                m_info_helper.merge ( i.GetAll () );
            }
        }
        catch (DBusExecutionException dee)
        {
            dee.printStackTrace ();
        }
        finally
        {
            m_info_helper.refresh ();
        }
    }

    public PlayerInfoHelper info ()
    {
        return m_info_helper;
    }

    public String service () { return m_bootstrap.service; }

    public String busname ()
    {
        return IFACE_MPRIS2 + "." + m_bootstrap.bus;
    }

    public String object ()
    {
        return OBJECT_MPRIS2;
    }

    public void execute ( ClientCommand comm, ServerResponse resp )
    {
        try
        {
            resp.resp = Response.Na;

            Act action = comm.action;
            String[] args = comm.args;
            switch ( action )
            {
                // MediaPlayer2
                case Raise:
                    m_mp2.Raise ();
                    break;
                case Quit:
                    m_mp2.Quit ();
                    break;
                case Fullscreen:
                    setProperty ( IFACE_MPRIS2, Fullscreen, Boolean.valueOf ( args [ 1 ] ) );
                    break;

                // MP2.Player
                case OpenUri:
                    m_mp2_play.OpenUri (
                            new File ( args [ 1 ] ).toURI ()
                                                   .toASCIIString ()
                                                   .replace ( ":/", ":///" )
                    );
                case Play:
                    m_mp2_play.Play ();
                    break;
                case PlayPause:
                    m_mp2_play.PlayPause ();
                    break;
                case Pause:
                    m_mp2_play.Pause ();
                    break;
                case Stop:
                    m_mp2_play.Stop ();
                    break;
                case FFwd:
                    m_mp2_play.Next ();
                    break;
                case FRev:
                    m_mp2_play.Previous ();
                    break;
                case Seek:
                    m_mp2_play.Seek ( Long.parseLong ( args [ 1 ], 10 ) );
                    break;
                case Volume:
                    setProperty ( IFACE_MPRIS2_PLAYER, Volume, Double.parseDouble ( args [ 1 ] ) );
                    break;
                case Shuffle:
                    setProperty ( IFACE_MPRIS2_PLAYER, Shuffle, Boolean.valueOf ( args [ 1 ] ) );
                    break;
                case Loop:
                    setProperty ( IFACE_MPRIS2_PLAYER, LoopStatus, LoopStatus.valueOf ( args [ 1 ] ) );
                    break;

                // MP2.Playlists
                case ActivatePlaylist:
                    if ( null != m_mp2_playlists )
                        m_mp2_playlists.ActivatePlaylist ( new Path ( args [ 1 ] ) );
                    break;
                case Playlists:
                    if ( null == m_mp2_playlists )
                        break;

                    resp.resp = Response.PlaylistInfo;

                    List < PlaylistInfo > playlists = new ArrayList <> ();
                    getAllPlaylists (
                             playlists,
                             PlaylistSort.valueOf ( args [ 1 ] ),
                             Boolean.valueOf ( args [ 2 ] )
                    );

                    resp.playlists = playlists.toArray ( new PlaylistInfo [ playlists.size () ] );
                    break;
                default:
                    break;
            }
        }
        catch ( Exception ex ) { ex.printStackTrace (); }
    }

    private <T> void setProperty ( String iface, String property, T value )
    {
        PropertiesHelper help = m_properties.get ( iface );

        if ( null == help )
            return;

        help.Set ( property, value );
    }

    private void getAllPlaylists ( Collection < PlaylistInfo > list, PlaylistSort sort, Boolean reverse )
    {
        UInt32 count = m_info_helper.getSafe ( PlaylistCount, UInt32.class );

        if ( null == count )
            return;

        List < MediaPlayer2.Struct1 > rv = m_mp2_playlists.GetPlaylists (
                new UInt32 ( 0 ), count, sort.toString (), reverse
        );

        for ( MediaPlayer2.Struct1 i : rv )
        {
            list.add ( new PlaylistInfo ( busname (), i.a.getPath (), i.b ) );
        }
    }

    /**
     * Merges the properties from a set of DBus objects into one payload
     */
    public class PlayerInfoHelper
    {
        private final Map < String, Variant > m_data;

        public final PlayerInfo m_info;

        public PlayerInfoHelper ()
        {
            this.m_data = new HashMap<> ();
            this.m_info = new PlayerInfo ();
        }

        public PlayerInfoHelper merge ( Map < String, Variant > fresh )
        {
            m_data.putAll ( fresh );
            return this;
        }

        public PlayerInfo refresh ()
        {
            m_info.id = busname ();

            m_info.name = name ();
            m_info.state = state ();
            m_info.loop = loop ();
            m_info.shuffle = shuffle ();
            m_info.fullscreen = getSafe ( Fullscreen, false );
            m_info.position = position ();
            m_info.volume = volume ();
            m_info.playlists = getSafe ( PlaylistCount, new UInt32 ( 0 ) ).intValue ();

            metadata ( m_info.metadata );
            capabilities ( m_info.capability );

            return m_info;
        }

        public PlayerInfoHelper clear ()
        {
            m_info.clear ();
            m_data.clear ();
            refresh ();
            return this;
        }

        public <T> T getSafe ( String key, Class<T> clz )
        {
            return variantSafe ( m_data.get ( key ), clz );
        }

        public <T> T getSafe ( String key, T def )
        {
            T t = getSafe ( key, (Class<T>)def.getClass () );
            return null == t ? def : t;
        }

        protected String name ()
        {
            return getSafe ( Identity, String.class );
        }

        protected Long position ()
        {
            return getSafe ( Position, Long.class );
        }

        protected PlayerState state ()
        {
            String st = getSafe ( PlaybackStatus, String.class );
            return null == st ? PlayerState.Inactive : PlayerState.valueOf ( st );
        }

        protected LoopState loop ()
        {
            String st = getSafe ( LoopStatus, String.class );
            return null == st ? LoopState.None : LoopState.valueOf ( st );
        }

        protected Boolean shuffle ()
        {
            return getSafe ( Shuffle, Boolean.class );
        }

        protected Double volume ()
        {
            return getSafe ( Volume, Double.class );
        }

        protected void metadata ( Metadata fill )
        {
            Map < String, Variant > data = getSafe ( Metadata, Map.class );

            if ( null != data )
            {
                fill.title   = variantSafe ( data.get ( MetadataConstants.Title ), String.class );
                fill.album   = variantSafe ( data.get ( MetadataConstants.Album ), String.class );
                fill.artists = variantSafe ( data.get ( MetadataConstants.Artist ), List.class );
                fill.length  = variantSafe ( data.get ( MetadataConstants.Length ), Long.class );
                fill.art_url = variantSafe ( data.get ( MetadataConstants.ArtUrl ), String.class );
                fill.url     = variantSafe ( data.get ( MetadataConstants.Url ), String.class );
                fill.trackId = variantSafe ( data.get ( MetadataConstants.TrackId ), String.class );
            }
        }

        protected void capabilities ( PlayerCapability fill )
        {
            fill.can_ctrl       = getSafe ( CanControl, false );
            fill.can_play       = getSafe ( CanPlay, false );
            fill.can_pause      = getSafe ( CanPause, false );
            fill.can_next       = getSafe ( CanGoNext, false );
            fill.can_prev       = getSafe ( CanGoPrevious, false );
            fill.can_seek       = getSafe ( CanSeek, false );
            fill.can_raise      = getSafe ( CanRaise, false );
            fill.can_fullscreen = getSafe ( CanFullscreen, false );
        }
    }

    public static <T> T variantSafe ( Variant v, Class <T> clz )
    {
        return null == v || !clz.isAssignableFrom ( v.getValue ().getClass () ) ? null : (T)v.getValue ();
    }
}
