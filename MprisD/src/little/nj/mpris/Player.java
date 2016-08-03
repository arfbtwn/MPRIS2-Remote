package little.nj.mpris;

import little.nj.CommonComponents;

import java.net.URI;
import java.util.List;

public interface Player
{
    void Raise ();
    void Quit ();

    void OpenUri( URI value );

    void Play ();
    void Toggle ();
    void Pause ();
    void Stop ();
    void Next ();
    void Prev ();

    void Seek ( Long value );

    void setFullscreen ( boolean value );
    void setVolume ( Double value );
    void setShuffle ( boolean value );
    void setRepeat( CommonComponents.LoopState value );

    List< CommonComponents.PlaylistInfo > getPlaylists( CommonComponents.PlaylistSort sort, boolean other );
    void setPlaylist( String id );
}
