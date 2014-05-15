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

package org.mpris;

import org.freedesktop.dbus.*;
import org.freedesktop.dbus.exceptions.DBusException;

import java.util.List;

public interface MediaPlayer2 extends DBusInterface
{
    public interface Player extends DBusInterface
    {
        public static class Seeked extends DBusSignal
        {
            public final long Position;
            public Seeked(String path, long Position) throws DBusException
            {
                super(path, Position);
                this.Position = Position;
            }
        }

        public void OpenUri(String uri);
        public void Next();
        public void Pause();
        public void Play();
        public void PlayPause();
        public void Previous();
        public void Seek(long Offset);
        public void SetPosition(DBusInterface TrackId, long Position);
        public void Stop();

    }

    public interface Playlists extends DBusInterface
    {
        public static class PlaylistChanged extends DBusSignal
        {
            public final Struct2 playlist;
            public PlaylistChanged(String path, Struct2 playlist) throws DBusException
            {
                super(path, playlist);
                this.playlist = playlist;
            }
        }

        public void ActivatePlaylist(Path playlist_id);
        public List<Struct1> GetPlaylists(UInt32 index, UInt32 max_count, String order, boolean reverse_order);

    }

    public final class Struct1 extends Struct
    {
        @Position(0)
        public final Path a;
        @Position(1)
        public final String b;
        @Position(2)
        public final String c;
        public Struct1(Path a, String b, String c)
        {
            this.a = a;
            this.b = b;
            this.c = c;
        }
    }

    public final class Struct2 extends Struct
    {
        @Position(0)
        public final Path a;
        @Position(1)
        public final String b;
        @Position(2)
        public final String c;
        public Struct2(Path a, String b, String c)
        {
            this.a = a;
            this.b = b;
            this.c = c;
        }
    }

    public void Quit();
    public void Raise();

}
