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

public class MprisConstants
{
    public static final String IFACE_MPRIS2           = "org.mpris.MediaPlayer2";
    public static final String IFACE_MPRIS2_PLAYER    = IFACE_MPRIS2 + ".Player";
    public static final String IFACE_MPRIS2_PLAYLISTS = IFACE_MPRIS2 + ".Playlists";

    public static final String OBJECT_MPRIS2  = "/org/mpris/MediaPlayer2";

    public static final String Identity       = "Identity";
    public static final String Volume         = "Volume";
    public static final String PlaybackStatus = "PlaybackStatus";
    public static final String LoopStatus     = "LoopStatus";
    public static final String Position       = "Position";
    public static final String Metadata       = "Metadata";
    public static final String Shuffle        = "Shuffle";

    public static final String CanQuit        = "CanQuit";
    public static final String CanRaise       = "CanRaise";

    public static final String CanControl     = "CanControl";
    public static final String CanGoNext      = "CanGoNext";
    public static final String CanGoPrevious  = "CanGoPrevious";
    public static final String CanPause       = "CanPause";
    public static final String CanPlay        = "CanPlay";
    public static final String CanSeek        = "CanSeek";
    public static final String CanFullscreen  = "CanSetFullscreen";

    public static final String Fullscreen     = "Fullscreen";

    public static final String PlaylistCount  = "PlaylistCount";

    public static class MetadataConstants
    {
        public static final String Title   = "xesam:title";
        public static final String Album   = "xesam:album";
        public static final String Artist  = "xesam:artist";
        public static final String Url     = "xesam:url";
        public static final String ArtUrl  = "mpris:artUrl";
        public static final String Length  = "mpris:length";
        public static final String TrackId = "mpris:trackid";
    }
}
