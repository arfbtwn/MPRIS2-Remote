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
import org.freedesktop.dbus.Position;
import org.freedesktop.dbus.Struct;
public final class Struct1 extends Struct
{
   @Position(0)
   public final int a;
   @Position(1)
   public final int b;
   @Position(2)
   public final int c;
   @Position(3)
   public final int d;
  public Struct1(int a, int b, int c, int d)
  {
   this.a = a;
   this.b = b;
   this.c = c;
   this.d = d;
  }
}
