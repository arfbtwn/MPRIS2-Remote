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

package org.freedesktop;

import org.freedesktop.dbus.DBusInterface;
import org.freedesktop.dbus.DBusSignal;
import org.freedesktop.dbus.Variant;
import org.freedesktop.dbus.exceptions.DBusException;

import java.util.Map;

public interface MediaPlayer extends DBusInterface
{
    public void Quit();
    public String Identity();

    public interface Player extends DBusInterface
    {
        public static class CapsChange extends DBusSignal
        {
            public final int a;
            public CapsChange(String path, int a) throws DBusException
            {
                super(path, a);
                this.a = a;
            }
        }

        public static class StatusChange extends DBusSignal
        {
            public final Struct2 a;
            public StatusChange(String path, Struct2 a) throws DBusException
            {
                super(path, a);
                this.a = a;
            }
        }

        public static class TrackChange extends DBusSignal
        {
            public final Map<String,Variant> a;
            public TrackChange(String path, Map<String,Variant> a) throws DBusException
            {
                super(path, a);
                this.a = a;
            }
        }

        public int PositionGet();
        public void PositionSet(int arg0);
        public int VolumeGet();
        public void VolumeSet(int arg0);
        public int GetCaps();
        public Map<String,Variant> GetMetadata();
        public Struct1 GetStatus();
        public void Repeat(boolean arg0);
        public void Play();
        public void Stop();
        public void Pause();
        public void Prev();
        public void Next();

    }

    public interface TrackList extends DBusInterface
    {
        public static class TrackListChange extends DBusSignal
        {
            public final int a;

            public TrackListChange(String path, int a) throws DBusException
            {
                super(path, a);
                this.a = a;
            }
        }

        public void Random(boolean arg0);

        public void Loop(boolean arg0);

        public void DelTrack(int arg0);

        public void AddTrack(String arg0, boolean arg1);

        public int GetLength();

        public int GetCurrentTrack();

        public Map<String, Variant> GetMetadata(int arg0);
    }
}
