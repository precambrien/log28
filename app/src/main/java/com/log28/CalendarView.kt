package com.log28

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.children
import androidx.fragment.app.Fragment
import com.kizitonwose.calendarview.model.CalendarDay
import com.kizitonwose.calendarview.model.CalendarMonth
import com.kizitonwose.calendarview.model.DayOwner
import com.kizitonwose.calendarview.ui.DayBinder
import com.kizitonwose.calendarview.ui.MonthHeaderFooterBinder
import com.kizitonwose.calendarview.ui.ViewContainer
import com.log28.databinding.CalendarDayBinding
import com.log28.databinding.CalendarMonthHeaderBinding
import com.log28.databinding.FragmentCalendarViewBinding
import io.realm.Realm
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import android.graphics.Typeface
import java.util.*


/**
 * A simple [Fragment] subclass.
 * Use the [CalendarView.newInstance] factory method to
 * create an instance of this fragment.
 */
class CalendarView : Fragment() {
    private val realm = Realm.getDefaultInstance()
    private var periodDateObjects = realm.getPeriodDates()

    //TODO use a tree for better calendar performance?
    private var periodDates = mutableListOf<Long>()
    private val cycleInfo = realm.getCycleInfo()

    private lateinit var binding: FragmentCalendarViewBinding
    private var selectedDate: LocalDate? = null
    private val today = LocalDate.now()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_calendar_view, container, false)
    }

    override fun onDestroy() {
        super.onDestroy()
        realm.close()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // we should have context at this point
        periodDates = predictFuturePeriods(periodDateObjects.map { d -> d.date }.toMutableList())

        periodDateObjects.addChangeListener { results, changeSet ->
            if (changeSet != null) {
                periodDates = predictFuturePeriods(periodDateObjects.map { d -> d.date }.toMutableList())
                binding.calendar.notifyCalendarChanged()
            }
        }

        cycleInfo.addChangeListener<CycleInfo> { _, changeSet ->
            if (changeSet != null) {
                periodDates = predictFuturePeriods(periodDateObjects.map { d -> d.date }.toMutableList())
                binding.calendar.notifyCalendarChanged()
            }
        }

        binding = FragmentCalendarViewBinding.bind(view)
        val daysOfWeek = daysOfWeekFromLocale()
        // days of week legend
        binding.legendLayout.root.children.forEachIndexed { index, view ->
            (view as TextView).apply {
                text = daysOfWeek[index].getDisplayName(TextStyle.SHORT_STANDALONE, Locale.getDefault()).toString()
                setTextColorRes(R.color.primaryText)
            }
        }

        // shows current month and allows scrolling to +/- 4 months
        val currentMonth = YearMonth.now()
        val startMonth = currentMonth.minusMonths(4)
        val endMonth = currentMonth.plusMonths(4)
        binding.calendar.setup(startMonth, endMonth, daysOfWeek.first())
        binding.calendar.scrollToMonth(currentMonth)

        class DayViewContainer(view: View) : ViewContainer(view) {
            // Will be set when this container is bound. See the dayBinder.
            lateinit var day: CalendarDay
            val textView = CalendarDayBinding.bind(view).dayText
            val today = LocalDate.now()

            init {
                textView.setOnClickListener {
                    if (day.owner == DayOwner.THIS_MONTH) {
                        if (!(day.date.isAfter(today))) {
                            val cal = day.date.toCalendar()
                            Log.d(TAG, "day clicked ${cal.formatDate()}")
                            (this@CalendarView.activity as? MainActivity)?.navToDayView(cal)
                        }
                    }
                }
            }
        }

        binding.calendar.dayBinder = object : DayBinder<DayViewContainer> {
            override fun create(view: View) = DayViewContainer(view)
            override fun bind(container: DayViewContainer, day: CalendarDay) {
                container.day = day
                val textView = container.textView
                textView.text = day.date.dayOfMonth.toString()

                if (day.owner == DayOwner.THIS_MONTH) {
                    textView.visibility = View.VISIBLE
                    val longDate = day.date.formatToLong()
                    if (longDate in periodDates) {
                        // period day
                        textView.setTextColorRes(R.color.white)
                        textView.setBackgroundResource(R.drawable.primary_selected_bg)
                    } else {
                        // normal day
                        textView.background = null
                        if (day.date.isAfter(today)) {
                            textView.setTextColorRes(R.color.secondaryText)
                        } else {
                            textView.setTextColorRes(R.color.primaryText)
                        }
                    }
                    if (day.date == today) {
                        textView.setTypeface(textView.typeface, Typeface.BOLD)
                    }
                } else {
                    textView.visibility = View.INVISIBLE
                }
            }
        }

        class MonthViewContainer(view: View) : ViewContainer(view) {
            val textView = CalendarMonthHeaderBinding.bind(view).HeaderText
        }
        binding.calendar.monthHeaderBinder = object : MonthHeaderFooterBinder<MonthViewContainer> {
            override fun create(view: View) = MonthViewContainer(view)
            override fun bind(container: MonthViewContainer, month: CalendarMonth) {
                val monthLocale = month.yearMonth.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()).toString()
                container.textView.text = "${monthLocale.toLowerCase().capitalize()} ${month.year}"
            }
        }
    }

    // TODO there might be an off by 1 error somewhere in here
    private fun predictFuturePeriods(periodDates: MutableList<Long>): MutableList<Long> {
        var cycleStart = periodDates.filter { item ->
            val previousDay = item.toCalendar()
            previousDay.add(Calendar.DAY_OF_MONTH, -1)
            previousDay.formatDate() !in periodDates
        }.max()?.toCalendar()
                ?: return periodDates

        //the earliest day we can predict the next period for is tomorrow
        val tomorrow = Calendar.getInstance()
        tomorrow.add(Calendar.DAY_OF_MONTH, 1)

        for (i in 1..3) {
            cycleStart.add(Calendar.DAY_OF_MONTH, cycleInfo.cycleLength)

            if (cycleStart.before(tomorrow))
                cycleStart = tomorrow

            val cycleDays = cycleStart.clone() as Calendar
            periodDates.add(cycleDays.formatDate())
            for (j in 2..cycleInfo.periodLength) {
                cycleDays.add(Calendar.DAY_OF_MONTH, 1)
                periodDates.add(cycleDays.formatDate())
            }
        }

        return periodDates
    }

    override fun onDestroyView() {
        super.onDestroyView()
        periodDateObjects.removeAllChangeListeners()
        cycleInfo.removeAllChangeListeners()
    }

    companion object {
        const val TAG = "CALVIEW"

        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @return A new instance of fragment CalendarView.
         */
        fun newInstance() = CalendarView()
    }

}

