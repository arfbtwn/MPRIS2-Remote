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

import java.util.Queue;

/**
 * A simple threaded queue runner, saved me writing this
 * boilerplate twice...
 *
 * This class is practically thread safe, as long as you
 * don't do stupid things like trying to modify the queue
 * passed in at construction from outside this class.
 *
 * @param <T> Type of element to work with
 */
class QueueRunner < T > implements Runnable
{
    public static interface Action < T > {
        void act ( T x );
    }

    private final Object m_lock;
    private final Queue< T > m_queue;
    private final Action< T > m_act;

    public QueueRunner ( Queue< T > queue, Action< T > act )
    {
        this.m_lock = new Object ();
        this.m_queue = queue;
        this.m_act = act;
    }

    @Override
    public void run ()
    {
        T item;
        do {
            try
            {
                synchronized ( m_lock )
                {
                    while ( m_queue.isEmpty () )
                    {
                        m_lock.wait ();
                    }

                    item = m_queue.poll ();
                }

                m_act.act ( item );
            }
            catch ( InterruptedException ie ) { }

        } while ( !Thread.interrupted () );
    }

    public final void offer ( T e )
    {
        synchronized ( m_lock )
        {
            m_queue.offer ( e );
            m_lock.notify ();
        }
    }
}
