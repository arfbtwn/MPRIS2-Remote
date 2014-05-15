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
import org.freedesktop.dbus.Position;
import org.freedesktop.dbus.Tuple;
/** Just a typed container class */
public final class Pair <A,B> extends Tuple
{
   @Position(0)
   public final A a;
   @Position(1)
   public final B b;
   public Pair(A a, B b)
   {
      this.a = a;
      this.b = b;
   }
}
