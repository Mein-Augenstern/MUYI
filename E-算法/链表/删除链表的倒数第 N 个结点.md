删除链表的倒数第 N 个结点
====

一、题目（LeetCode 19 · 中等 · Hot100）
------

给定链表头节点 `head` 与整数 `n`，删除链表的**倒数第 n 个结点**并返回头节点。题目保证 n 合法。进阶：**一趟扫描**实现。

```
输入：head = 1 -> 2 -> 3 -> 4 -> 5, n = 2
输出：1 -> 2 -> 3 -> 5     // 删除倒数第 2 个（值为 4）
```

二、思路演进
------

### 2.1 两趟：先求长度再定位

第一遍求长度 L，倒数第 n 个即正数第 L - n + 1 个。第二遍从头走 L - n 步，停在待删节点的**前驱**上执行删除。正确但扫了两遍——"求长度"这一趟就是为了把"倒数"换算成"正数"。

### 2.2 关键洞察：快慢指针保持固定间隔，让"倒数"自动换算

让快指针**先走 n+1 步**（从哑结点出发），快慢之间恒定隔着 n + 1 个节点。随后两者**同速**前进，快指针走到 null 时，慢指针恰好停在待删节点的**前驱**：

* 快指针总共走 L + 1 步（哑结点到 null），慢指针走 L + 1 - (n + 1) = L - n 步；
* 从哑结点数 L - n 步，正是倒数第 n 个节点的前驱。

全程一趟、不需要知道 L，"倒数"被间隔直接换算掉了。这是与 [环形链表 I](环形链表 I.md) "速度差"不同的另一种双指针用法：**间隔差**。

### 2.3 哑结点为什么必要

n == L（删除头节点）时，头节点没有前驱，不设哑结点就得单独写"删头"分支。哑结点给所有节点（含头）统一提供了前驱，一套逻辑通吃。

三、复杂度分析
------

* 时间 O(L)：快指针走满全程，慢指针走的步数不超过快指针。对比两趟法省掉的正是"先量一遍长度"的整趟。
* 空间 O(1)：哑结点、两指针。

四、代码
------

```java
package org.example;

/**
 * @author MUYI
 * @version 1.0
 * @date 2026/10/17
 */
public class LeetCode_19 {

    public static void main(String[] args) {
        ListNode node = removeNthFromEnd(build(new int[]{1, 2, 3, 4, 5}), 2);
        print(node);                                     // 期望 1 2 3 5

        ListNode single = removeNthFromEnd(build(new int[]{1}), 1);
        print(single);                                   // 期望空（删除的就是头）
    }

    public static ListNode removeNthFromEnd(ListNode head, int n) {
        ListNode dummy = new ListNode(0);
        dummy.next = head;

        // 快指针从哑结点先走 n+1 步：与慢指针拉开 n+1 的间隔
        ListNode fast = dummy;
        ListNode slow = dummy;
        for (int i = 0; i <= n; i++) {      // n+1 步：i 从 0 到 n
            fast = fast.next;
        }
        while (fast != null) {              // 同速前进，fast 到 null 时
            fast = fast.next;               // slow 恰停在待删节点的前驱
            slow = slow.next;
        }
        slow.next = slow.next.next;         // 删除；题目保证 n 合法，slow.next 必非空
        return dummy.next;
    }

    static class ListNode {
        int val;
        ListNode next;
        ListNode(int val) { this.val = val; }
    }

    static ListNode build(int[] values) {
        ListNode dummy = new ListNode(0);
        ListNode tail = dummy;
        for (int v : values) {
            tail.next = new ListNode(v);
            tail = tail.next;
        }
        return dummy.next;
    }

    static void print(ListNode head) {
        for (ListNode p = head; p != null; p = p.next) {
            System.out.print(p.val + " ");
        }
        System.out.println();
    }

}
```

五、坑点
------

* **先走的步数与起点必须配套**：从哑结点走 n+1 步、while 条件 `fast != null` 是一组。改成"从 head 走 n 步、`fast.next != null`"也成立，但两处参数交叉混用会差一位。写完必须用 n=1、n=L 两个边界各验一遍。
* **`for (int i = 0; i <= n; i++)` 是 n+1 步**：手滑写成 `< n`，慢指针停在待删节点本身，删除的是它后一个。
* **删除头节点的场景靠哑结点覆盖**：`[1], n=1` 返回 null，正是 dummy.next 兜住的分支。
* **工程上要防御 n > 链长**：题目保证了 n 合法。快速指针先走阶段会提前踩 null。真实代码应加校验而不是 NPE。
* 被删节点可 `del.next = null` 帮助 GC（可选，不影响正确性）。

六、变形追问
------

* **变体一（链表的中间结点，LeetCode 876）**：快 2 慢 1，快到尾时慢在中点。与本题同族："用两个指针的**速度差或间隔差**把位置关系自动换算出来"。可顺带复习 [环形链表 I](环形链表 I.md)。
* **变体二（删除链表中的节点，LeetCode 237）**：只给**被删节点**不给前驱——思路完全不同。把后继的值复制过来，删除后继。追问"为什么删不掉尾节点"能暴露对链表内存模型的理解。再延伸：删除倒数第 N 个的**只读版**（返回节点不删）就是快慢间隔的退化用法。（链表家族其余篇目：[反转链表](反转链表.md)、[合并两个有序链表](合并两个有序链表.md)）。

> 2026-10 新增（E 轨 · Hot100 第一批）。
