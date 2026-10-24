环形链表 I
====

一、题目（LeetCode 141 · 简单 · Hot100）
------

给定链表头节点 `head`，判断链表中**是否有环**：若某节点可以通过持续跟踪 `next` 指针再次到达，则存在环。返回布尔值。

```
输入：3 -> 2 -> 0 -> 4，且 4 的 next 指回 2
输出：true
```

进阶：能否用 O(1) 空间解决。（标题里的 I 是与 LeetCode 142「环形链表 II」——找入环点——区分。）

二、思路演进
------

### 2.1 朴素：HashSet 记录走过的节点

每个节点入 Set，走到已见过的节点即有环；走到 null 即无环。时间 O(n)、空间 O(n)，能过，但面试官必追问 **O(1) 空间**。

### 2.2 关键洞察：Floyd 快慢指针——环内追及问题

快指针每轮走 2 步、慢指针每轮走 1 步：

* **无环**：快指针先摸到 null，循环自然结束；
* **有环**：两者迟早都进环。此后两者的**相对速度恒为 1**，环内距离差每轮恰好减 1。3、2、1、0 连续下降，**必然在某轮减到 0，即相遇**。

不会"跳过"的原因：相对速度是 1，距离差从正整数逐次减 1，不会从 1 直接变到 -1。反过来，若快指针每轮走 3 步（速度差 2），距离差按 2 递减对环长取模。环长为偶数、初始差为奇数时，就永远踩不到 0。**"差 1"不是随便选的，是必然相遇的充要节奏**。

### 2.3 时间上界的推导

相遇总步数 = 慢指针入环前的直线段（≤ n）+ 入环后至多再走一圈（差距 ≤ 环长 r，每轮缩 1）→ 整体线性。

三、复杂度分析
------

* 时间 O(n)：如上拆成"直线段 + 环内追及"两段，各自线性；快指针步数是慢指针两倍，仍是常数倍。
* 空间 O(1)：两个指针，对比 HashSet 的 O(n)。

四、代码
------

```java
package org.example;

/**
 * @author MUYI
 * @version 1.0
 * @date 2026/10/17
 */
public class LeetCode_141 {

    public static void main(String[] args) {
        // 3 -> 2 -> 0 -> 4，尾接回下标 1，成环
        ListNode n1 = new ListNode(3);
        ListNode n2 = new ListNode(2);
        ListNode n3 = new ListNode(0);
        ListNode n4 = new ListNode(4);
        n1.next = n2;
        n2.next = n3;
        n3.next = n4;
        n4.next = n2;
        System.out.println(hasCycle(n1));    // 期望 true

        // 1 -> 2，无环
        ListNode m1 = new ListNode(1);
        m1.next = new ListNode(2);
        System.out.println(hasCycle(m1));    // 期望 false
    }

    public static boolean hasCycle(ListNode head) {
        if (head == null || head.next == null) {
            return false;
        }
        ListNode slow = head;
        ListNode fast = head;
        // 条件顺序不能反：fast 为 null 时先取 fast.next 会 NPE
        while (fast != null && fast.next != null) {
            slow = slow.next;                // 慢者一步
            fast = fast.next.next;           // 快者两步，相对速度恒为 1
            if (slow == fast) {              // 移动之后再比（起点相同，先比必相等）
                return true;
            }
        }
        return false;                        // 快者摸到 null：没有环
    }

    static class ListNode {
        int val;
        ListNode next;
        ListNode(int val) { this.val = val; }
    }

}
```

五、坑点
------

* **while 条件顺序**：`fast != null && fast.next != null`——短路顺序保证取 `fast.next` 前 fast 已非空，写反直接 NPE。
* **先移动后比较**：快慢同从 head 出发，不移动就比必然"相等"误报 true。
* **别拿 `fast.next.next != null` 当循环条件**：fast 停在倒数第二格时循环提前退出，可能漏判。
* **单节点自环**（head.next == head）要走一轮才相遇，上面的逻辑天然覆盖；开头的单节点判空只是剪枝，删除也正确。
* 该解法依赖"每节点只有一个 next"的单链结构。树或一般图上判回访必须回到 visited 集合语义，快慢指针不适用。

六、变形追问
------

* **变体一（环形链表 II，LeetCode 142）**：找入环点。相遇后让一个指针回到 head，两者改**同速**前进，再次相遇处即入环点。推导：设头到入环 a、入环到相遇点 c、环长 L。相遇时慢者走 a+c、快者走 a+c+kL 且为两倍。解得 a = (k-1)L + (L-c) = c' + 若干圈。从 head 和相遇点同速出发，必在入环点会和。
* **变体二（追问）**："慢指针入环后至多走一圈就被追上吗？"——是：入环瞬间距离差 ≤ r，每轮缩 1，至多 r 轮相遇，慢者至多走 r 步，不满一圈。这题考察的是对追及过程的上界有没有量级感（同族双指针题见 [删除链表的倒数第 N 个结点](删除链表的倒数第 N 个结点.md)）。

> 2026-10 新增（E 轨 · Hot100 第一批）。
