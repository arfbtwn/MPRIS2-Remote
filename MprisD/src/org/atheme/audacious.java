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

package org.atheme;
import java.util.List;
import org.freedesktop.dbus.DBusInterface;
import org.freedesktop.dbus.UInt32;
import org.freedesktop.dbus.Variant;
public interface audacious extends DBusInterface
{

  public String GetActivePlaylistName();
  public void EqualizerActivate(boolean active);
  public void SetEqBand(int band, double value);
  public void SetEqPreamp(double preamp);
  public void SetEq(double preamp, List<Double> bands);
  public double GetEqBand(int band);
  public double GetEqPreamp();
  public Pair<Double, List<Double>> GetEq();
  public void PlaylistEnqueueToTemp(String url);
  public boolean PlayqueueIsQueued(int pos);
  public void PlayqueueClear();
  public void PlayqueueRemove(int pos);
  public void PlayqueueAdd(int pos);
  public void PlaylistAdd(String list);
  public void PlaylistInsUrlString(String url, int pos);
  public int GetPlayqueueLength();
  public void ToggleAot(boolean ontop);
  public Triplet<Integer, Integer, Integer> GetInfo();
  public UInt32 QueueGetQueuePos(UInt32 pos);
  public UInt32 QueueGetListPos(UInt32 qpos);
  public void PlayPause();
  public void ShowFilebrowser(boolean show);
  public void ShowJtfBox(boolean show);
  public void ShowAboutBox(boolean show);
  public void ShowPrefsBox(boolean show);
  public void ToggleStopAfter();
  public boolean StopAfter();
  public void ToggleShuffle();
  public boolean Shuffle();
  public void ToggleRepeat();
  public boolean Repeat();
  public void ToggleAutoAdvance();
  public boolean AutoAdvance();
  public void Clear();
  public void Delete(UInt32 pos);
  public void OpenListToTemp(List<String> filenames);
  public void OpenList(List<String> filenames);
  public void AddList(List<String> filenames);
  public void AddUrl(String url);
  public void Add(String file);
  public void Jump(UInt32 pos);
  public Variant SongTuple(UInt32 pos, String tuple);
  public int SongFrames(UInt32 pos);
  public int SongLength(UInt32 pos);
  public String SongFilename(UInt32 pos);
  public String SongTitle(UInt32 pos);
  public int Length();
  public void Reverse();
  public void Advance();
  public UInt32 Position();
  public int Balance();
  public void SetVolume(int vl, int vr);
  public Pair<Integer, Integer> Volume();
  public void Seek(UInt32 pos);
  public UInt32 Time();
  public Triplet<Integer, Integer, Integer> Info();
  public String Status();
  public boolean Stopped();
  public boolean Paused();
  public boolean Playing();
  public void Stop();
  public void Pause();
  public void Play();
  public List<String> GetTupleFields();
  public void ShowMainWin(boolean show);
  public boolean MainWinVisible();
  public void Eject();
  public void Quit();
  public String Version();

}
