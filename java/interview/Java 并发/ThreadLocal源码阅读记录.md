链接
● ThreadLocal 源码全详解（ThreadLocalMap）：https://juejin.cn/post/7113023112655929358

![image](https://github.com/Mein-Augenstern/MUYI/assets/34135120/95f3fca8-c1fd-46d9-99d7-0441298bc6e7)

## ThreadLocal的描述

ThreadLocal只是作为一个入口或资源句柄去给每个线程的threadLocals或inheritableThreadLocals变量，因为threadLocals是真实保存每个线程的私有信息，其中threadLocals变量的类型是ThreadLocal.ThreadLocalMap，而ThreadLocal.ThreadLocalMap是ThreadLocal的一个静态内部类，实际上ThreadLocal.ThreadLocalMap是完全可以独立出来作为一个类而单独存在的。
ThreadLocal的不光是作为入口或资源句柄去给外部线程获取自身的私有资源，同时也给每个ThreadLocal提供了存储到ThreadLocal.ThreadLocalMap中的hashCode计算方式。核心逻辑如下所示：

```java
/**
 * ThreadLocals rely on per-thread linear-probe hash maps attached
 * to each thread (Thread.threadLocals and
 * inheritableThreadLocals).  The ThreadLocal objects act as keys,
 * searched via threadLocalHashCode.  This is a custom hash code
 * (useful only within ThreadLocalMaps) that eliminates collisions
 * in the common case where consecutively constructed ThreadLocals
 * are used by the same threads, while remaining well-behaved in
 * less common cases.
 */
private final int threadLocalHashCode = nextHashCode();

/**
 * The next hash code to be given out. Updated atomically. Starts at
 * zero.
 */
private static AtomicInteger nextHashCode =
    new AtomicInteger();

/**
 * The difference between successively generated hash codes - turns
 * implicit sequential thread-local IDs into near-optimally spread
 * multiplicative hash values for power-of-two-sized tables.
 */
private static final int HASH_INCREMENT = 0x61c88647;

/**
 * Returns the next hash code.
 */
private static int nextHashCode() {
    return nextHashCode.getAndAdd(HASH_INCREMENT);
}
```
还有些作为句柄操作的方法：set、get、remove、SuppliedThreadLocal（通过提供一个Supplier（供应者）来初始化线程本地变量的值。在每个线程中，Supplier会被调用一次，返回初始值，并且每个线程都会有自己的独立的初始值。）等方法。

![image](https://github.com/Mein-Augenstern/MUYI/assets/34135120/c8e9667c-29e8-4449-91d1-fd4701c5a8a7)

## ThreadLocalMap的描述

ThreadLocal.ThreadLocalMap作为每个线程的资源或变量实际存储的载体入口，深入进入看ThreadLocal.ThreadLocalMap会发现其实内部是依靠ThreadLocal.ThreadLocalMap.Entry类是存储线程的资源或变量内容。即ThreadLocal.ThreadLocalMap其实是作为实际线程的资源或变量存储载体的入口。
但请记住ThreadLocalMap不仅仅只是利用Entry类存储线程的资源或变量，还提供了操作Entry的各种方法，比如ThreadLocal对外提供的set、get、remove均是通过ThreadLocalMap内部的set、get、remove方法实现的。这几个关键的方法分析相见如下：

![image](https://github.com/Mein-Augenstern/MUYI/assets/34135120/fe7c6717-4bf0-44ec-9339-df74de1ca23b)

接下来挨个分析下每个方法的实现：

### 构造方法
```java
/**
 * Construct a new map initially containing (firstKey, firstValue).
 * ThreadLocalMaps are constructed lazily, so we only create
 * one when we have at least one entry to put in it.
 */
ThreadLocalMap(ThreadLocal<?> firstKey, Object firstValue) {
	  // 默认大小为16
    table = new Entry[INITIAL_CAPACITY];

    // 计算第一个key再数组的下标位置
    int i = firstKey.threadLocalHashCode & (INITIAL_CAPACITY - 1);

    // 初始化数组第一个元素信息
    table[i] = new Entry(firstKey, firstValue);
    size = 1;

    // 初始化扩容阈值大小
    setThreshold(INITIAL_CAPACITY);
}
```

### entry的hash过程

```java
ThreadLocalMap 实现了自己的hash 算法来解决散列表数组冲突。
int i = key.threadLocalHashCode & (len - 1);

这里的 i 就是当前 key 在散列表中对应的数组下标位置。
len 指的是 ThreadLocalMap 当前的容量 capacity。
而比较重要的是我们必须知道 key.threadLocalHashCode 这个值是怎么计算的？
通过源码可以知道 threadLocalHashCode 是 ThreadLocal 的一个属性，
其值是调用 ThreadLocal 的 nextHahCode() 方法获得的。
nextHashCode()：返回 AtomicInteger nextHahCode 的值，并将 
AtomicInteger nextHahCode自增一个常量值 HASH_INCREMENT(0x61c88647)。
特别提醒：
每创建一个ThreadLocal对象（每将对象 hash 到 map 一次），
ThreadLocal.nextHashCode 就增长0x61c88647。
（0x61c88647 是斐波那契数，使用该数值作为 hash 增量可以使 hash 分布更加均匀。）

public class ThreadLocal<T> {
    private final int threadLocalHashCode = nextHashCode();

    private static AtomicInteger nextHashCode = new AtomicInteger();

    private static final int HASH_INCREMENT = 0x61c88647;

    private static int nextHashCode() {
        return nextHashCode.getAndAdd(HASH_INCREMENT);
    }

    static class ThreadLocalMap {
        ThreadLocalMap(ThreadLocal<?> firstKey, Object firstValue) {
            table = new Entry[INITIAL_CAPACITY];
            int i = firstKey.threadLocalHashCode & (INITIAL_CAPACITY - 1);

            table[i] = new Entry(firstKey, firstValue);
            size = 1;
            setThreshold(INITIAL_CAPACITY);
        }
    } 
}
```
总结：ThreadLocalMap 的hash 算法很简单，就是使用斐波那契数的倍数 和(len -1) 按位与（这个结果其实就是斐波那契数的倍数 对capacity 取模）的结果作为当前 key 在散列表中的数组下标。

### entry的hash冲突解决
HashMap 如何解决 hash 冲突：HashMap 解决冲突是使用链地址法，在数组上构造链表结构，将冲突的数据放在链表上，且每个数组元素也就是链表的长度超过某个数量后会将链表转换为红黑树。
ThreadLocalMap 使用的是线性探测的开放地址法去解决 hash 冲突。 当当前 key 存在 hash 冲突，会线性地往后探测直到找到为 null 的位置存入对象，或者找到 key 相同的位置覆盖更新原来的对象。在这过程中若发现不为空但 key 为 null 的桶（key 过期的 Entry 数据）则启动探测式清理操作。

### get & getEntryAfterMiss - 获取ThreadLocal实例对应的value
```java
/**
 * Get the entry associated with key.  This method
 * itself handles only the fast path: a direct hit of existing
 * key. It otherwise relays to getEntryAfterMiss.  This is
 * designed to maximize performance for direct hits, in part
 * by making this method readily inlinable.
 *
 * @param  key the thread local object
 * @return the entry associated with key, or null if no such
 */
private Entry getEntry(ThreadLocal<?> key) {
    int i = key.threadLocalHashCode & (table.length - 1);
    Entry e = table[i];

    // 进入此分支说明hash还未发生冲突
    if (e != null && e.get() == key)
        return e;

    // 进入此流程说明发生了hash冲突，接下来要去遍历寻找到数组元素
    else
        return getEntryAfterMiss(key, i, e);
}

/**
 * Version of getEntry method for use when key is not found in
 * its direct hash slot.
 *
 * @param  key the thread local object
 * @param  i the table index for key's hash code
 * @param  e the entry at table[i]
 * @return the entry associated with key, or null if no such
 */
private Entry getEntryAfterMiss(ThreadLocal<?> key, int i, Entry e) {
    Entry[] tab = table;
    int len = tab.length;

    // 从hash冲突开始处元素循环遍历获取元素e，并触发清理过期元素操作
    while (e != null) {
        ThreadLocal<?> k = e.get();
        if (k == key)
            return e;
        if (k == null)
            expungeStaleEntry(i);
        else
            i = nextIndex(i, len);
        e = tab[i];
    }
    return null;
}
```

### set-设置key（ThreadLocal实例）和value
```java
/**
 * Set the value associated with key.
 *
 * @param key the thread local object
 * @param value the value to be set
 */
private void set(ThreadLocal<?> key, Object value) {

    // We don't use a fast path as with get() because it is at
    // least as common to use set() to create new entries as
    // it is to replace existing ones, in which case, a fast
    // path would fail more often than not.

    // 此处的注释翻译：
    // 我们不像使用get()那样使用快速路径，
    // 因为使用set()创建新条目和替换现有条目一样普遍，
    // 如果使用快速路径，失败的可能性比较大。
    // 其中的快速路径指的是：

  	// 获取 key（ThreadLocal实例）在Entry数组中的位置下标
    Entry[] tab = table;
    int len = tab.length;
    int i = key.threadLocalHashCode & (len-1);

    // 下面的循环能进入的前提是：参数key在Entry数组中的位置下标上的第一个节点不为空,
    // 即第一次线程进入这个方法是，直接跳过循环执行new 数组元素的操作。
    // 从位置 i 开始遍历Entry数组，直到 e 为空就跳出循环
    // 观察for循环中逻辑细节：被循环得到的元素e若不等于入参key，且不为空，则继续循环，
    // 之所以会出现这种情况原因是因为hash冲突导致
    // 遍历的目的是为了找到入参key对应的容器元素e（数组元素），并设置 e 的value
    for (Entry e = tab[i]; e != null; e = tab[i = nextIndex(i, len)]) {
        ThreadLocal<?> k = e.get();

        // 如果 e 的key等于入参中的key，则直接覆盖e的value
        if (k == key) {
            e.value = value;
            return;
        }

        // 如果 e 的key为空，则执行替换过去 value 操作
        // 即若目标数组下标上元素为空，则使用替换过期节点操作执行赋值操作
        if (k == null) {
            replaceStaleEntry(key, value, i);
            return;
        }
    }

    // 在数组下标 i 处创建并赋值 新的entry对象
    tab[i] = new Entry(key, value);
    int sz = ++size;

    // 条件一和条件二两个均成功才会去扩容
    // 条件一：查找并清除过期的条目成功
    // 条件二：扩容后的长度大于了需要扩容的阈值
    // cleanSomeSlots方法作用是清除部分过期数组元素
    if (!cleanSomeSlots(i, sz) && sz >= threshold)
        rehash();
}
```

### remove-清理ThreadLocal实例

结合hash冲突解决方法就明白为什么需要先根据hashCode计算数组元素下标后，再去遍历数组元素了。
```java
/**
 * Remove the entry for key.
 */
private void remove(ThreadLocal<?> key) {
    Entry[] tab = table;
    int len = tab.length;
    int i = key.threadLocalHashCode & (len-1);
    for (Entry e = tab[i];
         e != null;
         e = tab[i = nextIndex(i, len)]) {
        if (e.get() == key) {
            e.clear();
            expungeStaleEntry(i);
            return;
        }
    }
}
```

### rehash-重新hash

```java
/**
 * Re-pack and/or re-size the table. First scan the entire
 * table removing stale entries. If this doesn't sufficiently
 * shrink the size of the table, double the table size.
 */
private void rehash() {
    expungeStaleEntries();

    // Use lower threshold for doubling to avoid hysteresis
    if (size >= threshold - threshold / 4)
        resize();
}
```

### resize-扩容
```java
/**
 * // 将表的容量加倍。
 * Double the capacity of the table.
 */
private void resize() {
    // 扩容准备，将扩容前长度和对象保存到变量中
    Entry[] oldTab = table;
    int oldLen = oldTab.length;
    int newLen = oldLen * 2;
    Entry[] newTab = new Entry[newLen];
    int count = 0;

    // 遍历老的数组元素，并设置到新的数组中
    for (int j = 0; j < oldLen; ++j) {
        Entry e = oldTab[j];
        if (e != null) {
            ThreadLocal<?> k = e.get();
            if (k == null) {
                e.value = null; // Help the GC
            } else {
                // 根据新数组的长度和要重置的key实例的hashcode计算存储到新数据的下标
                int h = k.threadLocalHashCode & (newLen - 1);

                // 获取数组元素设置到新数组的下标
                // 使用线性探测的开放地址法去寻找实例 k 应该存储的最终位置
                while (newTab[h] != null)
                    h = nextIndex(h, newLen);
                newTab[h] = e;
                count++;
            }
        }
    }

  	// 重新设置扩容阈值
    setThreshold(newLen);
    size = count;
    table = newTab;
}
```

### expungeStaleEntries-清理所有的过去节点
```java
/**
 * 清除表中的所有陈旧条目。
 * Expunge all stale entries in the table.
 */
private void expungeStaleEntries() {
    Entry[] tab = table;
    int len = tab.length;
    for (int j = 0; j < len; j++) {
        Entry e = tab[j];
        // 清除数组中元素节点中 key 属性为空的数组元素信息
        if (e != null && e.get() == null)
            expungeStaleEntry(j);
    }
}
```

### replaceStaleEntry-替换过期节点

此方法并不是简单的只是清理入参中传入的staleSlot节点下的元素，而是会向前、向后分别遍历过期节点信息并执行区间清理动作，但实际执行时，只会向前或向后发生区间清理动作和staleSlot清理节点动作。
```java
/**
 * // 在进行设置操作时，用指定键的条目替换遇到的陈旧条目。
 * // 传递给值参数的值存储在该条目中，无论指定键是否已经存在条目。
 * Replace a stale entry encountered during a set operation
 * with an entry for the specified key.  The value passed in
 * the value parameter is stored in the entry, whether or not
 * an entry already exists for the specified key.
 *
 * // 作为副作用，此方法清除包含陈旧条目的“运行”中的所有陈旧条目。 
 * //（运行是两个空槽之间的条目序列。）
 * As a side effect, this method expunges all stale entries in the
 * "run" containing the stale entry.  (A run is a sequence of entries
 * between two null slots.)
 *
 * @param  key the key
 * @param  value the value to be associated with key
 * @param  staleSlot index of the first stale entry encountered while
 *         searching for key.
 */
private void replaceStaleEntry(ThreadLocal<?> key, Object value,
                               int staleSlot) {
    Entry[] tab = table;
    int len = tab.length;
    Entry e;

  	// 从入参过期节点位置开始，前置查找key为空的数组下标，复制给slotToExpunge
    // 从而在slotToExpunge和staleSlot 两个数组下标之间形成清理区间。

    // 返回以检查当前运行中之前的陈旧条目。我们一次清除整个运行，
    // 以避免由于垃圾收集器释放引用而产生的不断增量的再散列（即，每当收集器运行时）。
    // Back up to check for prior stale entry in current run.
    // We clean out whole runs at a time to avoid continual
    // incremental rehashing due to garbage collector freeing
    // up refs in bunches (i.e., whenever the collector runs).
    int slotToExpunge = staleSlot;
    for (int i = prevIndex(staleSlot, len);
         (e = tab[i]) != null;
         i = prevIndex(i, len))
        if (e.get() == null)
            slotToExpunge = i;

    // 找到运行中的键或尾随的空槽，以先出现的为准。
    // 从入参过期节点位置开始，后置查找key为空的数组下标，复制给
    // Find either the key or trailing null slot of run, whichever
    // occurs first
    for (int i = nextIndex(staleSlot, len);
         (e = tab[i]) != null;
         i = nextIndex(i, len)) {
        ThreadLocal<?> k = e.get();

      	// 如果真的走到了下面的方法，就直接执行交换并清理操作后，直接就返回了 
        
        // 如果我们找到了键，那么我们需要将其与陈旧的条目交换，以保持哈希表的顺序。
        // 然后，新的陈旧插槽，或者在其上遇到的任何其他陈旧插槽，
        // 可以发送到expungeStaleEntry来移除或重新哈希运行中的所有其他条目。
        // If we find key, then we need to swap it
        // with the stale entry to maintain hash table order.
        // The newly stale slot, or any other stale slot
        // encountered above it, can then be sent to expungeStaleEntry
        // to remove or rehash all of the other entries in run.
        if (k == key) {
            e.value = value;

            tab[i] = tab[staleSlot];
            tab[staleSlot] = e;

            // 从先前的陈旧条目开始进行清除（如果存在）。
            // Start expunge at preceding stale entry if it exists
            if (slotToExpunge == staleSlot)
                slotToExpunge = i;

            // 启发式清理数据
            cleanSomeSlots(expungeStaleEntry(slotToExpunge), len);
            return;
        }

        // 如果在向后扫描时没有找到陈旧的条目，
        // 那么在查找键时看到的第一个陈旧条目仍然存在于该运行中。
        // If we didn't find stale entry on backward scan, the
        // first stale entry seen while scanning for key is the
        // first still present in the run.
        if (k == null && slotToExpunge == staleSlot)
            slotToExpunge = i;
    }

    // If key not found, put new entry in stale slot
    tab[staleSlot].value = null;
    tab[staleSlot] = new Entry(key, value);

    // 如果运行中还有其他陈旧的条目，请将它们清除。
    // If there are any other stale entries in run, expunge them
    if (slotToExpunge != staleSlot)
        cleanSomeSlots(expungeStaleEntry(slotToExpunge), len);
}
```
其中replaceStaleEntry之所以向前遍历和向后遍历的设计思路，可以参考下面的回答：
作者：王俊杰 hybrid
链接：https://www.zhihu.com/question/412041096/answer/1913570393
来源：知乎
著作权归作者所有。商业转载请联系作者获得授权，非商业转载请注明出处。
1.前向搜索，出现key为null的Entry肯定是因为上次GC了，而之所以去前向搜索，是因为很有可能其它Entry在上次GC中也没能存活。另外并不是【相邻位置有很大概率会出现stale entry】，而是因为它只能一个个遍历，所以从【相邻】的位置开始遍历
2.向后遍历，是因为ThreadLocal用的是开地址，很可能当前的stale entry对应的并不是hascode为此槽索引的Entry，而是因为哈希冲突后移的Entry，那么很有可能hascode对应该槽的Entry会往后排。基于Thread Local Map中不允许有两个槽指向同一个引用的原则，如果存在那个hascode对应本槽但是在后面排列的Entry，则要【向后遍历】找到它，并且替换至本槽。否则直接设置值就会在Thread Local Map中存在两个指向一个ThreadLocal引用的槽

### cleanSomeSlots-循环log2N次，并执行清理过期节点动作

```java
/**
 * // 启发式地扫描一些单元，寻找陈旧的条目。
 * // 当添加新元素或清除另一个陈旧元素时会调用此方法。
 * // 它执行对数数量的扫描，以在不进行扫描（快速但保留垃圾）
 * // 和扫描数量与元素数量成比例（可以找到所有垃圾，但会导致一些插入操作需要 O(n) 时间）
 * // 之间取得平衡。
 * Heuristically scan some cells looking for stale entries.
 * This is invoked when either a new element is added, or
 * another stale one has been expunged. It performs a
 * logarithmic number of scans, as a balance between no
 * scanning (fast but retains garbage) and a number of scans
 * proportional to number of elements, that would find all
 * garbage but would cause some insertions to take O(n) time.
 *
 * @param i a position known NOT to hold a stale entry. The
 * scan starts at the element after i.
 *
 * @param n scan control: {@code log2(n)} cells are scanned,
 * unless a stale entry is found, in which case
 * {@code log2(table.length)-1} additional cells are scanned.
 * When called from insertions, this parameter is the number
 * of elements, but when from replaceStaleEntry, it is the
 * table length. (Note: all this could be changed to be either
 * more or less aggressive by weighting n instead of just
 * using straight log n. But this version is simple, fast, and
 * seems to work well.)
 *
 * @return true if any stale entries have been removed.
 */
private boolean cleanSomeSlots(int i, int n) {
    boolean removed = false;
    Entry[] tab = table;
    int len = tab.length;
    do {
        i = nextIndex(i, len);
        Entry e = tab[i];
        if (e != null && e.get() == null) {
            n = len;
            removed = true;
            // 清理过期节点
            i = expungeStaleEntry(i);
        }
    } 
          // n >>>= 1 说明要循环log2N次。在没有发现脏Entry时，
          // 会一直往后找下个位置的entry是否是脏的，如果是的话，就会使 n = 数组的长度。
          // 然后继续循环log2新N 次。
    while ( (n >>>= 1) != 0);
    
    return removed;
}
```
### expungeStaleEntry-清理过期节点

```java
/**
 * Expunge a stale entry by rehashing any possibly colliding entries
 * lying between staleSlot and the next null slot.  This also expunges
 * any other stale entries encountered before the trailing null.  See
 * Knuth, Section 6.4
 *
 * @param staleSlot index of slot known to have null key
 * @return the index of the next null slot after staleSlot
 * (all between staleSlot and this slot will have been checked
 * for expunging).
 */
private int expungeStaleEntry(int staleSlot) {
    Entry[] tab = table;
    int len = tab.length;

    // 清除staleSlot（过期槽点）处的条目
    // 第一步：先清楚数组中下标中元素的属性value值的内容
    // 第二步：再清除数组中下标出的元素内容
    // expunge entry at staleSlot
    tab[staleSlot].value = null;
    tab[staleSlot] = null;

    // 缩小数组长度
    size--;

  	// 重新散列，直到我们遇到空槽。
    // Rehash until we encounter null
    Entry e;
    int i;
    for (// 获取过期节点的下一个数组元素
         i = nextIndex(staleSlot, len);
         // 直到数组元素e为空时，就跳出遍历
    		 (e = tab[i]) != null;
         // 递增获取数组中下一位元素
         i = nextIndex(i, len)) {
         
        ThreadLocal<?> k = e.get();

        // 数组元素e的key为空时，清理value后，可跳出循环
        if (k == null) {
            e.value = null;
            tab[i] = null;
            size--;
        } 
        // 重新计算staleSlot后的数组元素位置
        else {
            // 重新计算被清理staleSlot元素e后续的每个元素e的位置下标
            // 之所以重新计算是因为 len - 1 了。在本方法中for循环上面size执行了size--操作
            // 比较重新计算后的位置下标和之前的位置下标是否相同，
            // 若不相同，则重新移动数组元素位置到计算完成后的数组下标h处
            int h = k.threadLocalHashCode & (len - 1);
            if (h != i) {
                // 先清理数组元素i处的内容
                tab[i] = null;

              	// 使用线性探测的开放地址法去寻找实例 k 应该存储的最终位置
                // Unlike Knuth 6.4 Algorithm R, we must scan until
                // null because multiple entries could have been stale.
                while (tab[h] != null)
                    h = nextIndex(h, len);
                tab[h] = e;
            }
        }
    }
    return i;
}
```

### ThreadLocal.ThreadLocalMap.Entry的描述

先看Entry的源码，非常简单，牢记Entry继承了父类们提供了Key存储的落脚地（属性接收Key，即：java.lang.ref.Reference#referent属性作为承载Key的落脚点），所以Entry实际上有Key，Value两个属性，其中Key是ThreadLocal实例，Value是Object类型：具体类型是在ThreadLocal实例属性定义时设置进去的，可以理解为Entry是一个Map，其中key是ThreadLocal实例，而value是ThreadLocal实例存储的数据即线程私有的资源或变量，至于为什么是数组是因为一个线程可以有多个ThreadLocal实例属性。
并牢记此ThreadLocal实例是作为属性绑定到Thread中的threadLocals属性上，所以当在代码中get()的时候，流程是先获取当前线程，再获取线程的threadLocals属性值后，即拿到了资源存储的句柄：ThreadLocalMap后，再把ThreadLocal实例传入ThreadLocalMap中，获取最终实际存储当前线程在ThreadLocal变量中存储的数据T，数据T在设计的时候用了范型技术，所以在实际使用的过程中value可以存储任意的类型。
```java
/**
 * The entries in this hash map extend WeakReference, using
 * its main ref field as the key (which is always a
 * ThreadLocal object).  Note that null keys (i.e. entry.get()
 * == null) mean that the key is no longer referenced, so the
 * entry can be expunged from table.  Such entries are referred to
 * as "stale entries" in the code that follows.
 */
static class Entry extends WeakReference<ThreadLocal<?>> {
    /** The value associated with this ThreadLocal. */
    Object value;

    Entry(ThreadLocal<?> k, Object v) {
        super(k);
        value = v;
    }
}
```

### Thread中ThreadLocal初始化的时间-GPT3.5

ThreadLocal在Thread中的初始化时间是在第一次访问ThreadLocal变量时进行的。
当线程第一次调用ThreadLocal的get()方法或set()方法时，会触发ThreadLocal的初始化过程。在初始化过程中，会调用ThreadLocal的initialValue()方法来获取初始值，该方法是ThreadLocal类的一个protected方法，可以被子类重写以提供自定义的初始值。
需要注意的是，每个ThreadLocal对象的initialValue()方法只会在首次访问时被调用一次，后续访问同一个ThreadLocal对象时，将直接获取之前初始化的值，不再调用initialValue()方法。
因此，ThreadLocal的初始化是延迟的，仅在第一次访问线程局部变量时进行，以确保每个线程都有自己的独立副本。这种延迟初始化的机制可以节省资源，并且在需要时才进行初始化，提高了性能。

### ThreadLocal中0x61c88647含义

```java
/**
 * The difference between successively generated hash codes - turns
 * implicit sequential thread-local IDs into near-optimally spread
 * multiplicative hash values for power-of-two-sized tables.
 */
private static final int HASH_INCREMENT = 0x61c88647;

// 这里可以看作一个自增的步长，把线性的id转换为用于2的幂次方表的近似最佳分布的乘法哈希值。
```
线性探测法有个问题是，一旦发生碰撞，很可能之后每次都会产生碰撞，导致连环撞车。而使用0x61c88647这个值做一个hash的增长值就可以从一定程度上解决这个问题让生成出来的值较为均匀地分布在2的幂大小的数组中。也就是说当我们用0x61c88647作为步长累加为每个ThreadLocal分配各自的ID也就是threadLocalHashCode再与2的幂取模，得到的结果分布很均匀。
0x61c88647选取其实是与斐波那契散列有关，这个就是数学知识了，这里不展开。
来自GPT3.5的回答
在ThreadLocal中，HASH_INCREMENT的值被设置为0x61c88647，是为了在ThreadLocal中的哈希冲突情况下提供更好的散列分布。
ThreadLocal中使用线性探测哈希映射来存储每个线程的ThreadLocal变量副本。当多个ThreadLocal对象的threadLocalHashCode（通过ThreadLocal类的hashCode()方法获取）发生冲突时，线性探测哈希映射需要通过增加一个固定的增量来重新计算哈希索引，以寻找下一个可用的位置。
为了最大程度地减少冲突，ThreadLocal的设计者选择了一个特殊的固定增量值0x61c88647，这是一个魔术数字。这个值被证明在实践中可以提供相对均匀的哈希分布，减少线性探测带来的冲突。
使用这个特殊的固定增量值可以使得连续创建的ThreadLocal对象在同一线程中使用时，发生的冲突概率较低，提高了线性探测哈希映射的性能。

### ThreadLocalMap中threshold属性的作用

在set方法中检测是否需要扩容Entry数组的容量。

### 为什么ThreadLocalMap使用数组？

使用数组的原因是，一个线程可以有多个ThreadLocal来存放不同类型的数据，这些数据都会放到当前线程的ThreadLocalMap里，所以需要选择数组来存。

### ThreadLocal本身存储值么？

不存储。


