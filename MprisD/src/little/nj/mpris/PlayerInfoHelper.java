package little.nj.mpris;

import little.nj.CommonComponents;
import org.freedesktop.dbus.UInt32;
import org.freedesktop.dbus.Variant;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static little.nj.mpris.MprisConstants.*;

/**
 * Merges the properties from a set of DBus objects into one payload
 */
public class PlayerInfoHelper
{
    static <T> T variantSafe ( Variant v, Class <T> clz )
    {
        return null == v || !clz.isAssignableFrom ( v.getValue ().getClass () ) ? null : (T)v.getValue ();
    }

    private final Map< String, Variant > m_data;

    public final CommonComponents.PlayerInfo m_info;

    public PlayerInfoHelper ( String id )
    {
        this.m_data = new HashMap<> ();
        this.m_info = new CommonComponents.PlayerInfo ();

        this.m_info.id = id;
    }

    public PlayerInfoHelper merge ( Map < String, Variant > fresh )
    {
        m_data.putAll ( fresh );
        return this;
    }

    public CommonComponents.PlayerInfo refresh ()
    {
        m_info.name = name ();
        m_info.state = state ();
        m_info.loop = loop ();
        m_info.shuffle = shuffle ();
        m_info.fullscreen = getSafe ( Fullscreen, false );
        m_info.position = position ();
        m_info.volume = volume ();
        m_info.playlists = getSafe ( PlaylistCount, new UInt32 ( 0 ) ).intValue ();
        m_info.when = System.currentTimeMillis ();

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

    protected CommonComponents.PlayerState state ()
    {
        String st = getSafe ( PlaybackStatus, String.class );
        return null == st ? CommonComponents.PlayerState.Inactive : CommonComponents.PlayerState.valueOf ( st );
    }

    protected CommonComponents.LoopState loop ()
    {
        String st = getSafe ( LoopStatus, String.class );
        return null == st ? CommonComponents.LoopState.None : CommonComponents.LoopState.valueOf ( st );
    }

    protected Boolean shuffle ()
    {
        return getSafe ( Shuffle, Boolean.class );
    }

    protected Double volume ()
    {
        return getSafe ( Volume, Double.class );
    }

    protected void metadata ( CommonComponents.Metadata fill )
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

    protected void capabilities ( CommonComponents.PlayerCapability fill )
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
