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

package little.nj;

import little.nj.CommonComponents.*;
import little.nj.mpris.PlayerWrapper;
import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.Path;
import org.freedesktop.dbus.exceptions.DBusException;

import java.io.File;
import java.util.List;

public class ServerPlayerWrapper extends PlayerWrapper
{
    public ServerPlayerWrapper ( DBusConnection dbus, String busname, String service ) throws DBusException
    {
        super (dbus, busname, service );
    }

    public ServerPlayerWrapper ( DBusConnection dbus, BootstrapPlayerInfo info ) throws DBusException
    {
        super ( dbus, info );
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
                    Raise();
                    break;
                case Quit:
                    Quit();
                    break;
                case Fullscreen:
                    setFullscreen ( Boolean.valueOf ( args [ 1 ] ) );
                    break;

                // MP2.Player
                case OpenUri:
                    OpenUri ( new File ( args [ 1 ] ).toURI () );
                case Play:
                    Play ();
                    break;
                case PlayPause:
                    Toggle ();
                    break;
                case Pause:
                    Pause ();
                    break;
                case Stop:
                    Stop ();
                    break;
                case FFwd:
                    Next ();
                    break;
                case FRev:
                    Prev ();
                    break;
                case Seek:
                    Seek ( Long.parseLong ( args [ 1 ], 10 ) );
                    break;
                case Volume:
                    setVolume ( Double.parseDouble ( args [ 1 ] ) );
                    break;
                case Shuffle:
                    setShuffle ( Boolean.valueOf ( args [ 1 ] ) );
                    break;
                case Loop:
                    setRepeat ( LoopState.valueOf ( args [ 1 ] ) );
                    break;

                // MP2.Playlists
                case ActivatePlaylist:
                    if ( null != m_mp2_playlists )
                        m_mp2_playlists.ActivatePlaylist ( new Path ( args [ 1 ] ) );
                    break;
                case Playlists:
                    resp.resp = Response.PlaylistInfo;

                    List<PlaylistInfo> playlists = getPlaylists ( PlaylistSort.valueOf ( args [ 1 ] ), Boolean.valueOf ( args [ 2 ] ) );

                    resp.playlists = playlists.toArray ( new PlaylistInfo [0]);
                    break;
                default:
                    break;
            }
        }
        catch ( Exception ex ) { ex.printStackTrace (); }
    }
}
