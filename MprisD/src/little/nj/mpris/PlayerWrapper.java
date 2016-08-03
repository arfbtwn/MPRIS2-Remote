package little.nj.mpris;

import little.nj.CommonComponents;
import org.freedesktop.DBus;
import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.Path;
import org.freedesktop.dbus.UInt32;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.mpris.MediaPlayer2;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static little.nj.mpris.MprisConstants.*;

public class PlayerWrapper implements Player
{
    public static class BootstrapPlayerInfo
    {
        public final String bus;
        public final String service;
        public final String object;

        public BootstrapPlayerInfo ( String bus, String service, String object )
        {
            this.bus = bus;
            this.service = service;
            this.object = object;
        }

        public BootstrapPlayerInfo ( String bus, String service )
        {
            this(bus, service, OBJECT_MPRIS2);
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

    protected DBus.Properties m_prop;
    protected MediaPlayer2 m_mp2;
    protected MediaPlayer2.Player m_mp2_play;
    protected MediaPlayer2.Playlists m_mp2_playlists;

    public PlayerWrapper ( DBusConnection dbus, String busname, String service ) throws DBusException
    {
        this(dbus, new BootstrapPlayerInfo ( busname, service ));
    }

    public PlayerWrapper ( DBusConnection dbus, BootstrapPlayerInfo info ) throws DBusException
    {
        this.m_dbus = dbus;
        this.m_bootstrap = info;
        this.m_info_helper = new PlayerInfoHelper ( busname() );

        init ();
    }

    public void init () throws DBusException
    {
        getRemotes ();

        refresh ();
    }

    private void getRemotes () throws DBusException
    {
        m_prop = m_dbus.getRemoteObject ( busname (), object(), DBus.Properties.class );

        m_mp2 = m_dbus.getRemoteObject ( busname (), object (), MediaPlayer2.class );
        m_mp2_play = m_dbus.getRemoteObject ( busname (), object (), MediaPlayer2.Player.class );
        m_mp2_playlists = m_dbus.getRemoteObject ( busname (), object (), MediaPlayer2.Playlists.class );
    }

    public void refresh ()
    {
        try
        {
            m_info_helper.merge ( m_prop.GetAll ( MediaPlayer2.class.getCanonicalName () ) );
            m_info_helper.merge ( m_prop.GetAll ( MediaPlayer2.Player.class.getCanonicalName () ) );
            m_info_helper.merge ( m_prop.GetAll ( MediaPlayer2.Playlists.class.getCanonicalName () ) );
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

    public String service ()
    {
        return m_bootstrap.service;
    }

    public String busname ()
    {
        return m_bootstrap.bus;
    }

    public String object ()
    {
        return m_bootstrap.object;
    }

    <T> void setProperty ( String iface, String property, T value )
    {
        m_prop.Set ( iface, property, value );
    }

    void getAllPlaylists ( Collection< CommonComponents.PlaylistInfo > list, CommonComponents.PlaylistSort sort, Boolean reverse )
    {
        UInt32 count = m_info_helper.getSafe ( PlaylistCount, UInt32.class );

        if ( null == count )
            return;

        List < MediaPlayer2.Struct1 > rv = m_mp2_playlists.GetPlaylists (
                new UInt32 ( 0 ), count, sort.toString (), reverse
        );

        for ( MediaPlayer2.Struct1 i : rv )
        {
            list.add ( new CommonComponents.PlaylistInfo ( busname (), i.a.getPath (), i.b ) );
        }
    }

    @Override
    public void Quit ()
    {
        m_mp2.Quit ();
    }

    @Override
    public void OpenUri(URI value)
    {
        m_mp2_play.OpenUri ( value.toASCIIString ().replace ( ":/", ":///" ) );
    }

    @Override
    public void Play ()
    {
        m_mp2_play.Play ();
    }

    @Override
    public void Raise ()
    {
        m_mp2.Raise ();
    }

    @Override
    public void Toggle ()
    {
        m_mp2_play.PlayPause ();
    }

    @Override
    public void Pause ()
    {
        m_mp2_play.Pause ();
    }

    @Override
    public void Stop ()
    {
        m_mp2_play.Stop ();
    }

    @Override
    public void Next ()
    {
        m_mp2_play.Next ();
    }

    @Override
    public void Prev ()
    {
        m_mp2_play.Previous ();
    }

    @Override
    public void Seek ( Long value )
    {
        m_mp2_play.Seek ( value );
    }

    @Override
    public void setFullscreen ( boolean value )
    {
        setProperty ( IFACE_MPRIS2, Fullscreen, value );
    }

    @Override
    public void setVolume ( Double value )
    {
        setProperty ( IFACE_MPRIS2_PLAYER, Volume, value );
    }

    @Override
    public void setShuffle ( boolean value )
    {
        setProperty ( IFACE_MPRIS2_PLAYER, Shuffle, value );
    }

    @Override
    public void setRepeat ( CommonComponents.LoopState value )
    {
        setProperty ( IFACE_MPRIS2_PLAYER, LoopStatus, value.toString () );
    }

    @Override
    public List< CommonComponents.PlaylistInfo > getPlaylists ( CommonComponents.PlaylistSort sort, boolean other )
    {
        List < CommonComponents.PlaylistInfo > playlists = new ArrayList<> ();

        getAllPlaylists ( playlists, sort, other );

        return playlists;
    }

    @Override
    public void setPlaylist ( String id )
    {
        if ( null != m_mp2_playlists ) m_mp2_playlists.ActivatePlaylist ( new Path ( id ) );
    }
}
