package net.akehurst.kotlin.compose.layout.multipane

import net.akehurst.kotlin.compose.components.SplitOrientation
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class test_LayoutNode {

    @BeforeTest
    fun before() {
      //  LayoutNode.next = 0
    }

    @Test
    fun Tabbed_insertPaneViaAddTab_at_root_BEFORE() {
        // given
        val layout = layoutNode {
            tabbed(id="tabbed_1",) {
                pane(id = "pane_1_1", title = "Pane A") {  }
            }
        }
        val newPane = Pane(id = "pane_new", title = "Pane B") {  }
        val dropTarget = DropTarget.Tabbed(RectInWindow.Zero, DropTarget.Tabbed.Kind.BEFORE, "tabbed_1", "pane_1_1")
        // when
        val actual = layout.insertPaneIntoLayout(newPane, dropTarget)

        //then
        val expected = layoutNode {
            tabbed(id="tabbed_1",) {
                pane(id = "pane_new", title = "Pane B") {  }
                pane(id = "pane_1_1", title = "Pane A") {  }
            }
        }
        assertEquals(expected.asString(), actual.asString())
    }

    @Test
    fun Tabbed_insertPaneViaAddTab_at_root_AFTER() {
        // given
        val layout = layoutNode {
            tabbed(id="tabbed_1",) {
                pane(id = "pane_1_1", title = "Pane A") {  }
            }
        }
        val newPane = Pane(id = "pane_new", title = "Pane B") {  }
        val dropTarget = DropTarget.Tabbed(RectInWindow.Zero, DropTarget.Tabbed.Kind.AFTER, "tabbed_1", "pane_1_1")
        // when
        val actual = layout.insertPaneIntoLayout(newPane, dropTarget)

        //then
        val expected = layoutNode {
            tabbed(id="tabbed_1",) {
                pane(id = "pane_1_1", title = "Pane A") {  }
                pane(id = "pane_new", title = "Pane B") {  }
            }
        }
        assertEquals(expected.asString(), actual.asString())
    }

    @Test
    fun Tabbed_insertPaneViaAddTab_within_split_tab1_BEFORE() {
        // given
        val layout = layoutNode {
            split(id="split_1", orientation = SplitOrientation.Horizontal,) {
                tabbed(weight=1f, id = "tabbed_1_1",) {
                    pane(id = "pane_1_1_1", title = "Pane A") { }
                }
                tabbed(weight=1f, id = "tabbed_1_2",) {
                    pane(id = "pane_1_2_1", title = "Pane B") { }
                }
            }
        }
        val newPane = Pane(id = "pane_new", title = "Pane C") {  }
        val dropTarget = DropTarget.Tabbed(RectInWindow.Zero, DropTarget.Tabbed.Kind.BEFORE, "tabbed_1_1", "pane_1_1_1")
        // when
        val actual = layout.insertPaneIntoLayout(newPane, dropTarget)

        //then
        val expected = layoutNode {
            split(id="split_1", orientation = SplitOrientation.Horizontal,) {
                tabbed(weight=1f, id = "tabbed_1_1",) {
                    pane(id = "pane_new", title = "Pane C") { }
                    pane(id = "pane_1_1_1", title = "Pane A") { }
                }
                tabbed(weight=1f, id = "tabbed_1_2",) {
                    pane(id = "pane_1_2_1", title = "Pane B") { }
                }
            }
        }
        assertEquals(expected.asString(), actual.asString())
    }

    @Test
    fun Tabbed_insertPaneViaAddTab_within_split_tab1_AFTER() {
        // given
        val layout = layoutNode {
            split(id="split_1", orientation = SplitOrientation.Horizontal,) {
                tabbed(weight=1f, id = "tabbed_1_1",) {
                    pane(id = "pane_1_1_1", title = "Pane A") { }
                }
                tabbed(weight=1f, id = "tabbed_1_2",) {
                    pane(id = "pane_1_2_1", title = "Pane B") { }
                }
            }
        }
        val newPane = Pane(id = "pane_new", title = "Pane C") {  }
        val dropTarget = DropTarget.Tabbed(RectInWindow.Zero, DropTarget.Tabbed.Kind.AFTER, "tabbed_1_1", "pane_1_1_1")
        // when
        val actual = layout.insertPaneIntoLayout(newPane, dropTarget)

        //then
        val expected = layoutNode {
            split(id="split_1", orientation = SplitOrientation.Horizontal,) {
                tabbed(weight=1f, id = "tabbed_1_1",) {
                    pane(id = "pane_1_1_1", title = "Pane A") { }
                    pane(id = "pane_new", title = "Pane C") { }
                }
                tabbed(weight=1f, id = "tabbed_1_2",) {
                    pane(id = "pane_1_2_1", title = "Pane B") { }
                }
            }
        }
        assertEquals(expected.asString(), actual.asString())
    }

    @Test
    fun Tabbed_insertPaneViaAddTab_within_split_tab2_BEFORE() {
        // given
        val layout = layoutNode {
            split(id="split_1", orientation = SplitOrientation.Horizontal,) {
                tabbed(weight=1f, id = "tabbed_1_1",) {
                    pane(id = "pane_1_1_1", title = "Pane A") { }
                }
                tabbed(weight=1f, id = "tabbed_1_2",) {
                    pane(id = "pane_1_2_1", title = "Pane B") { }
                }
            }
        }
        val newPane = Pane(id = "pane_new", title = "Pane C") {  }
        val dropTarget = DropTarget.Tabbed(RectInWindow.Zero, DropTarget.Tabbed.Kind.BEFORE, "tabbed_1_2", "pane_1_2_1")
        // when
        val actual = layout.insertPaneIntoLayout(newPane, dropTarget)

        //then
        val expected = layoutNode {
            split(id="split_1", orientation = SplitOrientation.Horizontal,) {
                tabbed(weight=1f, id = "tabbed_1_1",) {
                    pane(id = "pane_1_1_1", title = "Pane A") { }
                }
                tabbed(weight=1f, id = "tabbed_1_2",) {
                    pane(id = "pane_new", title = "Pane C") { }
                    pane(id = "pane_1_2_1", title = "Pane B") { }
                }
            }
        }
        assertEquals(expected.asString(), actual.asString())
    }

    @Test
    fun Tabbed_insertPaneViaAddTab_within_split_tab2_AFTER() {
        // given
        val layout = layoutNode {
            split(id="split_1", orientation = SplitOrientation.Horizontal,) {
                tabbed(weight=1f, id = "tabbed_1_1",) {
                    pane(id = "pane_1_1_1", title = "Pane A") { }
                }
                tabbed(weight=1f, id = "tabbed_1_2",) {
                    pane(id = "pane_1_2_1", title = "Pane B") { }
                }
            }
        }
        val newPane = Pane(id = "pane_new", title = "Pane C") {  }
        val dropTarget = DropTarget.Tabbed(RectInWindow.Zero, DropTarget.Tabbed.Kind.AFTER, "tabbed_1_2", "pane_1_2_1")
        // when
        val actual = layout.insertPaneIntoLayout(newPane, dropTarget)

        //then
        val expected = layoutNode {
            split(id="split_1", orientation = SplitOrientation.Horizontal,) {
                tabbed(weight=1f, id = "tabbed_1_1",) {
                    pane(id = "pane_1_1_1", title = "Pane A") { }
                }
                tabbed(weight=1f, id = "tabbed_1_2",) {
                    pane(id = "pane_1_2_1", title = "Pane B") { }
                    pane(id = "pane_new", title = "Pane C") { }
                }
            }
        }
        assertEquals(expected.asString(), actual.asString())
    }

    @Test
    fun Tabbed_insertPaneViaNewSplit_at_root_TOP() {
        // given
        val layout = layoutNode {
            tabbed(id="tabbed_1",) {
                pane(id = "pane_1_1", title = "Pane A") {  }
            }
        }
        val newPane = Pane(id = "pane_new", title = "Pane B") {  }
        val dropTarget = DropTarget.Split(RectInWindow.Zero, DropTarget.Split.Kind.TOP, "tabbed_1", "pane_1_1")
        // when
        val actual = layout.insertPaneIntoLayout(newPane, dropTarget)

        //then
        val expected = layoutNode {
            split(id="id1", orientation = SplitOrientation.Vertical) {
                tabbed(weight = 0.5f,id = "id0",) {
                    pane(id = "pane_new", title = "Pane B") { }
                }
                tabbed(weight = 0.5f,id = "tabbed_1",) {
                    pane(id = "pane_1_1", title = "Pane A") { }
                }
            }
        }
        assertEquals(expected.asString(), actual.asString())
    }

    @Test
    fun Tabbed_insertPaneViaNewSplit_at_root_RIGHT() {
        // given
        val layout = layoutNode {
            tabbed(id="tabbed_1",) {
                pane(id = "pane_1_1", title = "Pane A") {  }
            }
        }
        val newPane = Pane(id = "pane_new", title = "Pane B") {  }
        val dropTarget = DropTarget.Split(RectInWindow.Zero, DropTarget.Split.Kind.RIGHT, "tabbed_1", "pane_1_1")
        // when
        val actual = layout.insertPaneIntoLayout(newPane, dropTarget)

        //then
        val expected = layoutNode {
            split(id="id1", orientation = SplitOrientation.Horizontal) {
                tabbed(weight = 0.5f,id = "tabbed_1",) {
                    pane(id = "pane_1_1", title = "Pane A") { }
                }
                tabbed(weight = 0.5f,id = "id0",) {
                    pane(id = "pane_new", title = "Pane B") { }
                }
            }
        }
        assertEquals(expected.asString(), actual.asString())
    }

    @Test
    fun Tabbed_insertPaneViaNewSplit_at_root_BOTTOM() {
        // given
        val layout = layoutNode {
            tabbed(id="tabbed_1",) {
                pane(id = "pane_1_1", title = "Pane A") {  }
            }
        }
        val newPane = Pane(id = "pane_new", title = "Pane B") {  }
        val dropTarget = DropTarget.Split(RectInWindow.Zero, DropTarget.Split.Kind.BOTTOM, "tabbed_1", "pane_1_1")
        // when
        val actual = layout.insertPaneIntoLayout(newPane, dropTarget)

        //then
        val expected = layoutNode {
            split(id="id1", orientation = SplitOrientation.Vertical) {
                tabbed(weight = 0.5f,id = "tabbed_1",) {
                    pane(id = "pane_1_1", title = "Pane A") { }
                }
                tabbed(weight = 0.5f,id = "id0",) {
                    pane(id = "pane_new", title = "Pane B") { }
                }
            }
        }
        assertEquals(expected.asString(), actual.asString())
    }

    @Test
    fun Tabbed_insertPaneViaNewSplit_at_root_LEFT() {
        // given
        val layout = layoutNode {
            tabbed(id="tabbed_1",) {
                pane(id = "pane_1_1", title = "Pane A") {  }
            }
        }
        val newPane = Pane(id = "pane_new", title = "Pane B") {  }
        val dropTarget = DropTarget.Split(RectInWindow.Zero, DropTarget.Split.Kind.LEFT, "tabbed_1", "pane_1_1")
        // when
        val actual = layout.insertPaneIntoLayout(newPane, dropTarget)

        //then
        val expected = layoutNode {
            split(id="id1", orientation = SplitOrientation.Horizontal) {
                tabbed(weight = 0.5f,id = "id0",) {
                    pane(id = "pane_new", title = "Pane B") { }
                }
                tabbed(weight = 0.5f,id = "tabbed_1",) {
                    pane(id = "pane_1_1", title = "Pane A") { }
                }
            }
        }
        assertEquals(expected.asString(), actual.asString())
    }

    @Test
    fun Tabbed_insertPaneViaNewSplit_at_root_Reorder__fail() {
        // given
        val layout = layoutNode {
            tabbed(id="tabbed_1",) {
                pane(id = "pane_1_1", title = "Pane A") {  }
            }
        }
        val newPane = Pane(id = "pane_new", title = "Pane B") {  }
        val dropTarget = DropTarget.Reorder(RectInWindow.Zero, DropTarget.Reorder.Kind.BEFORE, "tabbed_1", "pane_1_1")
        // when
        try {
            val actual = layout.insertPaneIntoLayout(newPane, dropTarget)
        } catch (t:Throwable) {
            assertTrue (t is IllegalStateException)
            assertEquals("Cannot Reorder a pane into a tabbed container: DropTarget.Reorder BEFORE tabbed_1 pane_1_1 RectInWindow(value=Rect.fromLTRB(0.0, 0.0, 0.0, 0.0))", t.message)
        }
        //then

    }


    @Test
    fun Split_insertPaneViaNewSplit_at_root_tab1_TOP() {
        // given
        val layout = layoutNode {
            split(id="split_1", orientation = SplitOrientation.Horizontal,) {
                tabbed(weight=1f, id = "tabbed_1_1",) {
                    pane(id = "pane_1_1_1", title = "Pane A") { }
                }
                tabbed(weight=1f, id = "tabbed_1_2",) {
                    pane(id = "pane_1_2_1", title = "Pane B") { }
                }
            }
        }
        val newPane = Pane(id = "pane_new", title = "Pane C") {  }
        val dropTarget = DropTarget.Split(RectInWindow.Zero, DropTarget.Split.Kind.TOP, "split_1", "tabbed_1_1")
        // when
        val actual = layout.insertPaneIntoLayout(newPane, dropTarget)

        //then
        val expected = layoutNode {
            split(id="split_1", orientation = SplitOrientation.Horizontal) {
                split(weight=0.5f,id="id1", orientation = SplitOrientation.Vertical) {
                    tabbed(weight = 0.5f, id = "id0",) {
                        pane(id = "pane_new", title = "Pane C") { }
                    }
                    tabbed(weight = 0.5f, id = "tabbed_1_1",) {
                        pane(id = "pane_1_1_1", title = "Pane A") { }
                    }
                }
                tabbed(weight=1f, id = "tabbed_1_2",) {
                    pane(id = "pane_1_2_1", title = "Pane B") { }

                }
            }
        }
        assertEquals(expected.asString(), actual.asString())
    }

    @Test
    fun Split_insertPaneViaNewSplit_at_root_tab1_RIGHT() {
        // given
        val layout = layoutNode {
            split(id="split_1", orientation = SplitOrientation.Horizontal,) {
                tabbed(weight=1f, id = "tabbed_1_1",) {
                    pane(id = "pane_1_1_1", title = "Pane A") { }
                }
                tabbed(weight=1f, id = "tabbed_1_2",) {
                    pane(id = "pane_1_2_1", title = "Pane B") { }
                }
            }
        }
        val newPane = Pane(id = "pane_new", title = "Pane C") {  }
        val dropTarget = DropTarget.Split(RectInWindow.Zero, DropTarget.Split.Kind.RIGHT, "split_1", "tabbed_1_1")
        // when
        val actual = layout.insertPaneIntoLayout(newPane, dropTarget)

        //then
        val expected = layoutNode {
            split(id="split_1", orientation = SplitOrientation.Horizontal) {
                split(weight=0.5f,id="id1", orientation = SplitOrientation.Horizontal) {
                    tabbed(weight = 0.5f, id = "tabbed_1_1",) {
                        pane(id = "pane_1_1_1", title = "Pane A") { }
                    }
                    tabbed(weight = 0.5f, id = "id0",) {
                        pane(id = "pane_new", title = "Pane C") { }
                    }
                }
                tabbed(weight=1f, id = "tabbed_1_2",) {
                    pane(id = "pane_1_2_1", title = "Pane B") { }

                }
            }
        }
        assertEquals(expected.asString(), actual.asString())
    }

    @Test
    fun Split_insertPaneViaNewSplit_at_root_tab1_BOTTOM() {
        // given
        val layout = layoutNode {
            split(id="split_1", orientation = SplitOrientation.Horizontal,) {
                tabbed(weight=1f, id = "tabbed_1_1",) {
                    pane(id = "pane_1_1_1", title = "Pane A") { }
                }
                tabbed(weight=1f, id = "tabbed_1_2",) {
                    pane(id = "pane_1_2_1", title = "Pane B") { }
                }
            }
        }
        val newPane = Pane(id = "pane_new", title = "Pane C") {  }
        val dropTarget = DropTarget.Split(RectInWindow.Zero, DropTarget.Split.Kind.BOTTOM, "split_1", "tabbed_1_1")
        // when
        val actual = layout.insertPaneIntoLayout(newPane, dropTarget)

        //then
        val expected = layoutNode {
            split(id="split_1", orientation = SplitOrientation.Horizontal) {
                split(weight=0.5f,id="id1", orientation = SplitOrientation.Vertical) {
                    tabbed(weight = 0.5f, id = "tabbed_1_1",) {
                        pane(id = "pane_1_1_1", title = "Pane A") { }
                    }
                    tabbed(weight = 0.5f, id = "id0",) {
                        pane(id = "pane_new", title = "Pane C") { }
                    }
                }
                tabbed(weight=1f, id = "tabbed_1_2",) {
                    pane(id = "pane_1_2_1", title = "Pane B") { }

                }
            }
        }
        assertEquals(expected.asString(), actual.asString())
    }

    @Test
    fun Split_insertPaneViaNewSplit_at_root_tab1_LEFT() {
        // given
        val layout = layoutNode {
            split(id="split_1", orientation = SplitOrientation.Horizontal,) {
                tabbed(weight=1f, id = "tabbed_1_1",) {
                    pane(id = "pane_1_1_1", title = "Pane A") { }
                }
                tabbed(weight=1f, id = "tabbed_1_2",) {
                    pane(id = "pane_1_2_1", title = "Pane B") { }
                }
            }
        }
        val newPane = Pane(id = "pane_new", title = "Pane C") {  }
        val dropTarget = DropTarget.Split(RectInWindow.Zero, DropTarget.Split.Kind.LEFT, "split_1", "tabbed_1_1")
        // when
        val actual = layout.insertPaneIntoLayout(newPane, dropTarget)

        //then
        val expected = layoutNode {
            split(id="split_1", orientation = SplitOrientation.Horizontal) {
                split(weight=0.5f,id="id1", orientation = SplitOrientation.Horizontal) {
                    tabbed(weight = 0.5f, id = "id0",) {
                        pane(id = "pane_new", title = "Pane C") { }
                    }
                    tabbed(weight = 0.5f, id = "tabbed_1_1",) {
                        pane(id = "pane_1_1_1", title = "Pane A") { }
                    }
                }
                tabbed(weight=1f, id = "tabbed_1_2",) {
                    pane(id = "pane_1_2_1", title = "Pane B") { }

                }
            }
        }
        assertEquals(expected.asString(), actual.asString())
    }
}