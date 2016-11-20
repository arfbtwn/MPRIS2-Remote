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

import java.io.File;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public class CommonComponents
{
    /**
     * The empty string
     */
    public static final String EMPTY = "";

    /**
     * Our default character set for m_data transmissions is UTF-8
     */
    public static final Charset DEFAULT_CHARSET = Charset.forName ( "UTF-8" );

    /**
     * The default port we run on, ephemeral
     */
    public static final int DEFAULT_PORT = 0;

    /**
     * The default service name we advertise through avahi
     */
    public static final String DEFAULT_SERVICE_TYPE = "_mpris._tcp";

    /**
     * The default server name we use
     */
    public static final String DEFAULT_SERVER_NAME = "MprisD";

    /**
     * Server acts
     */
    public enum Act
    {
        // Server
        Players, Power, Launch,
        Retrieve, Directory, Ping,

        // MP2
        Raise, Quit,

        // MP2.Player
        OpenUri,
        Play, PlayPause, Pause, Stop,
        FFwd, FRev, Seek,
        Volume,
        Loop, Shuffle,
        Fullscreen,

        // MP2.Playlist
        ActivatePlaylist, Playlists
    }

    /**
     * Clients send them to Servers
     */
    public static class ClientCommand
    {
        public ClientCommand ( Act act, String... argz )
        {
            this ( act, 0, argz );
        }

        public ClientCommand ( Act act, Integer requestId, String... argz )
        {
            action = act;
            id = requestId;
            args = argz;
        }

        public Act      action;
        public Integer  id;
        public String[] args;
    }

    /**
     * Response types Clients can expect from the Server
     */
    public enum Response {
        Na,           // Server flag, clients won't receive this
        PlayerInfo,   // An array of PlayerInfo is contained
        Deliver,      // A Base64 encoded string
        PlaylistInfo, // A set of playlists
        Directory,    // A directory listing
        Pong          // Pong?
    }

    /**
     * Server Reply
     */
    public static class ServerResponse
    {
        public boolean valid;

        public Response resp;

        public Integer id;

        public PlayerInfo[] players;

        public Base64Token  base64;

        public PlaylistInfo[] playlists;

        public DirectoryToken directory;
    }

    /**
     * A wrapper for the Deliver reply
     */
    public static class Base64Token implements Serializable
    {
        public String uri;
        public String b64;
    }

    /**
     * A wrapper for the Directory reply
     *
     * FIXME: It was intended to use URI for parsing
     *
     * Using URI would free us from needing to send the host's
     * separator character along with the token, the problem is
     * we are exposing some state, at least it's better than not
     * telling the client what we're using.
     */
    public static class DirectoryToken implements Serializable
    {
        public String sep;

        public String parent;

        public String name;

        public RemoteNode[] children;
    }

    public static class RemoteNode implements Serializable, Comparable < RemoteNode >
    {
        public RemoteNode ( String name, Boolean is_dir )
        {
            this.name = name;
            this.is_dir = is_dir;
        }

        public String name;
        public Boolean is_dir;

        @Override
        public String toString ()
        {
            return String.format ( "%s%s", name, is_dir ? File.separator : EMPTY );
        }

        @Override
        public int compareTo ( RemoteNode o )
        {
            return is_dir != o.is_dir ? is_dir ? -1 : 1 : name.compareTo ( o.name );
        }
    }

    /**
     * Player State
     * Note: Inactive is when the dbus object is not available
     */
    public enum PlayerState { Inactive, Playing, Paused, Stopped }

    /**
     * Repeat None, One or All
     */
    public enum LoopState { None, Track, Playlist }

    /**
     * Playlist sorting options
     */
    public enum PlaylistSort { Alphabetical, CreationDate, ModifiedDate, LastPlayDate, UserDefined }

    /**
     * Servers send them to Clients
     */
    public static class PlayerInfo implements Serializable
    {
        public PlayerInfo ()
        {
            metadata = new Metadata ();
            capability = new PlayerCapability ();
            clear ();
        }

        @Override
        public boolean equals ( Object o )
        {
            return o instanceof PlayerInfo && id.equals ( ((PlayerInfo)o).id );
        }

        @Override
        public int hashCode ()
        {
            return id.hashCode ();
        }

        @Override
        public String toString ()
        {
            return String.format ( "%s - %s", id, state );
        }

        /**
         * Basic information for the Player
         */
        public String      id;
        public String      name;
        public PlayerState state;
        public LoopState   loop;
        public Boolean     shuffle;
        public Boolean     fullscreen;
        public Long        position;
        public Double      volume;
        public Integer     playlists;
        public Long        when;

        /**
         * Metadata about the current track
         */
        public final Metadata metadata;

        /**
         * Current capabilities of the player
         */
        public final PlayerCapability capability;

        public PlayerInfo clear ()
        {
            name = EMPTY;
            state = PlayerState.Inactive;
            loop = LoopState.None;
            shuffle = Boolean.FALSE;
            fullscreen = false;
            position = 0L;
            volume = 0.0;
            playlists = 0;
            when = System.currentTimeMillis ();

            metadata.clear ();
            capability.clear ();

            return this;
        }
    }

    public static class PlayerCapability implements Serializable
    {
        public Boolean can_ctrl;
        public Boolean can_play;
        public Boolean can_pause;
        public Boolean can_next;
        public Boolean can_prev;
        public Boolean can_seek;
        public Boolean can_raise;
        public Boolean can_fullscreen;

        public PlayerCapability clear ()
        {
            can_ctrl = can_play = can_pause = can_next
                     = can_prev = can_seek = can_raise
                     = can_fullscreen
                               = Boolean.FALSE;
            return this;
        }
    }

    /**
     * Track metadata returned by the server
     */
    public static class Metadata implements Serializable
    {
        public Metadata ()
        {
            clear ();
        }

        public String        title;
        public String        album;
        public List <String> artists;
        public Long          length;
        public String        art_url;
        public String        url;
        public String        trackId;

        public String getFirstArtist ()
        {
            return null == artists || artists.isEmpty ()
                    ? EMPTY
                    : artists.iterator ().next ();
        }

        public Metadata clear ()
        {
            title = EMPTY;
            album = EMPTY;
            length = 0L;
            art_url = EMPTY;
            artists = new ArrayList <> ();
            url = EMPTY;
            trackId = EMPTY;

            return this;
        }

        @Override
        public String toString ()
        {
            return String.format ( "%s - %s", title, getFirstArtist () );
        }
    }

    public static class PlaylistInfo implements Serializable
    {
        public PlaylistInfo ( String player_id, String id, String name )
        {
            this.pid = player_id;
            this.id = id;
            this.name = name;
        }

        @Override
        public int hashCode ()
        {
            return (pid + id).hashCode ();
        }

        @Override
        public boolean equals ( Object obj )
        {
            if ( !(obj instanceof PlaylistInfo) )
                return false;

            PlaylistInfo that = (PlaylistInfo) obj;
            return (pid + id).equals ( that.pid + that.id );
        }

        public String pid;
        public String id;
        public String name;

        @Override
        public String toString ()
        {
            return name;
        }
    }
}
