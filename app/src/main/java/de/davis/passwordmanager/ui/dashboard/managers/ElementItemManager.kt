package de.davis.passwordmanager.ui.dashboard.managers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Point
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import de.davis.passwordmanager.R
import de.davis.passwordmanager.database.dtos.SecureElement
import de.davis.passwordmanager.ui.LinearLayoutManager
import de.davis.passwordmanager.ui.dashboard.viewholders.BasicViewHolder
import de.davis.passwordmanager.ui.dashboard.viewholders.SecureElementViewHolder

class ElementItemManager(
    initialItems: List<SecureElement>,
    onClick: ((SecureElement) -> Unit)?,
    private val fragmentManager: FragmentManager
) :
    AbsItemManager<SecureElement>(initialItems, onClick) {

    override fun createViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): BasicViewHolder<SecureElement> {
        return SecureElementViewHolder(
            LayoutInflater.from(parent.context),
            parent,
            fragmentManager
        )
    }

    override fun bind(
        viewHolder: BasicViewHolder<SecureElement>,
        filter: String,
        position: Int,
        selected: Boolean
    ) {
        super.bind(viewHolder, filter, position, selected)
        (viewHolder as SecureElementViewHolder).setLetterVisible(
            isHeader(position),
            items[position].letter
        )
    }

    override fun getViewType(position: Int): Int = 1


    override fun getLayoutManager(context: Context): RecyclerView.LayoutManager {
        return LinearLayoutManager(context)
    }

    override fun getItemId(position: Int): Long {
        return items[position].id
    }

    override fun MutableList<SecureElement>.sortItems() = sort()

    fun isHeader(position: Int): Boolean {
        val grouped = items.groupBy { it.letter }
        val item = items[position]
        return grouped[item.letter]?.firstOrNull() == item
    }

    override fun getItemDecoration(): RecyclerView.ItemDecoration {
        return object : RecyclerView.ItemDecoration() {
            private var header: TextView? = null

            private var defaultTranslationX: Float = -1f
            private var headerHeight: Int = 0

            private fun prepareHeaderView(itemPosition: Int, parent: ViewGroup): View {
                if (header == null) {
                    header = LayoutInflater.from(parent.context)
                        .inflate(R.layout.layout_element_letter_header, parent, false) as TextView

                    measureLayout(header!!, parent)
                }

                return header!!.apply {
                    text = items[itemPosition].letter.toString()
                }
            }

            private fun measureLayout(view: View, parent: ViewGroup) {
                val widthSpec =
                    View.MeasureSpec.makeMeasureSpec(parent.width, View.MeasureSpec.EXACTLY)
                val heightSpec = View.MeasureSpec.makeMeasureSpec(
                    parent.height,
                    View.MeasureSpec.UNSPECIFIED
                )

                val childWidth = ViewGroup.getChildMeasureSpec(
                    widthSpec,
                    parent.paddingLeft + parent.paddingRight,
                    view.layoutParams.width
                )
                val childHeight = ViewGroup.getChildMeasureSpec(
                    heightSpec,
                    parent.paddingTop + parent.paddingBottom,
                    view.layoutParams.height
                )

                view.measure(childWidth, childHeight)
                view.layout(0, 0, view.measuredWidth, view.measuredHeight)

                headerHeight = view.measuredHeight
            }

            private var lastInvisibleHeader: View? = null

            override fun onDrawOver(
                c: Canvas,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                super.onDrawOver(c, parent, state)


                val topChild = parent.getChildAt(0) ?: return
                val topChildPosition = parent.getChildAdapterPosition(topChild)
                if (topChildPosition == RecyclerView.NO_POSITION) return

                lastInvisibleHeader?.visibility = View.VISIBLE

                val topChildHeader = topChild.getHeader()

                prepareHeaderView(topChildPosition, parent).run {
                    val contactPoint = bottom
                    val childInContact = getChildInContact(parent, contactPoint)

                    if (defaultTranslationX < 0)
                        defaultTranslationX = getPositionRelativeToOtherView(
                            topChildHeader,
                            parent
                        ).x.toFloat()

                    if (childInContact != null) {
                        if (topChild != childInContact) {
                            moveHeader(
                                c,
                                this,
                                getPositionRelativeToOtherView(
                                    childInContact.getHeader(),
                                    parent
                                ).y.toFloat(),
                                defaultTranslationX
                            )
                        }
                        return
                    }

                    topChildHeader.visibility = View.INVISIBLE
                    lastInvisibleHeader = topChildHeader
                    drawHeader(c, this, defaultTranslationX)
                }
            }

            private fun getChildInContact(parent: RecyclerView, contactPoint: Int): View? {
                for (i in 0 until parent.childCount) {
                    val child = parent.getChildAt(i)
                    if (!isHeader(parent.getChildAdapterPosition(child)))
                        continue

                    val relPos = getRelativeTopAndBottom(
                        child.getHeader(),
                        parent
                    )


                    if (relPos.second > contactPoint && relPos.first <= contactPoint) {
                        return child
                    }
                }
                return null
            }


            fun getRelativeTopAndBottom(child: View, parent: View): Pair<Int, Int> {
                val parentLocation = IntArray(2)
                val childLocation = IntArray(2)

                // Choose either getLocationOnScreen or getLocationInWindow based on your needs
                parent.getLocationOnScreen(parentLocation)
                child.getLocationOnScreen(childLocation)

                val relativeTop = childLocation[1] - parentLocation[1]
                val relativeBottom = relativeTop + child.height

                return Pair(relativeTop, relativeBottom)
            }


            fun getPositionRelativeToOtherView(targetView: View, relativeToView: View): Point {
                val targetLocation = IntArray(2)
                val relativeLocation = IntArray(2)

                targetView.getLocationOnScreen(targetLocation)
                relativeToView.getLocationOnScreen(relativeLocation)

                val relativeX = targetLocation[0] - relativeLocation[0]
                val relativeY = targetLocation[1] - relativeLocation[1]

                return Point(relativeX, relativeY)
            }


            private fun moveHeader(
                c: Canvas,
                header: View,
                nextChildX: Float,
                x: Float
            ) {
                c.save()
                c.translate(x, nextChildX - header.height)
                header.draw(c)
                c.restore()
            }

            private fun drawHeader(c: Canvas, header: View, x: Float) {
                c.save()
                c.translate(x, 0f)
                header.draw(c)
                c.restore()
            }

            private fun View.getHeader(): View = findViewById(R.id.header)
        }
    }
}