package com.log28.intro

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.core.view.children
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import kotlinx.android.synthetic.main.fragment_last_period.*
import com.log28.R
import com.log28.formatDate
import com.log28.setFirstPeriod
import com.log28.databinding.FragmentLastPeriodBinding
import com.log28.databinding.CalendarMonthHeaderBinding
import com.log28.databinding.CalendarDayBinding
import com.log28.daysOfWeekFromLocale
import com.log28.setTextColorRes
import com.log28.toCalendar
import com.kizitonwose.calendarview.ui.DayBinder
import com.kizitonwose.calendarview.ui.MonthHeaderFooterBinder
import com.kizitonwose.calendarview.ui.ViewContainer
import com.kizitonwose.calendarview.model.CalendarDay
import com.kizitonwose.calendarview.model.CalendarMonth
import com.kizitonwose.calendarview.model.DayOwner
import java.util.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.DayOfWeek
import java.time.temporal.WeekFields
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import android.graphics.Typeface
import io.realm.Realm


class LastPeriodFragment : Fragment() {
    private val realm = Realm.getDefaultInstance()

    var dateSelected: Calendar? = null
    private lateinit var binding: FragmentLastPeriodBinding
    private var selectedDate: LocalDate? = null
    private val today = LocalDate.now()

    override fun onDestroy() {
        super.onDestroy()
        realm.close()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_last_period, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = FragmentLastPeriodBinding.bind(view)
        val daysOfWeek = daysOfWeekFromLocale()
        // days of week legend
        binding.legendLayout.root.children.forEachIndexed { index, d_view ->
            (d_view as TextView).apply {
                text = daysOfWeek[index].getDisplayName(TextStyle.SHORT_STANDALONE, Locale.getDefault()).toString()
                setTextColorRes(R.color.primaryText)
            }
        }

        // shows current month and allows scrolling to 4 months before
        val currentMonth = YearMonth.now()
        val startMonth = currentMonth.minusMonths(4)
        binding.calendar.setup(startMonth, currentMonth, daysOfWeek.first())
        binding.calendar.scrollToMonth(currentMonth)

        //TODO prevent a future date to be selected and set future days to R.color.secondaryText
        class DayViewContainer(view: View) : ViewContainer(view) {
            // Will be set when this container is bound. See the dayBinder.
            lateinit var day: CalendarDay
            val textView = CalendarDayBinding.bind(view).dayText

            init {
                textView.setOnClickListener {
                    if (day.owner == DayOwner.THIS_MONTH) {
                        if (!(day.date.isAfter(today))) {
                            if (selectedDate == day.date) {
                                // unclicking a previously selected date
                                selectedDate = null
                                binding.calendar.notifyDayChanged(day)
                                (this@LastPeriodFragment.activity as AppIntroActivity).setupComplete = false
                            } else {
                                val oldDate = selectedDate
                                selectedDate = day.date
                                binding.calendar.notifyDateChanged(day.date)
                                oldDate?.let { binding.calendar.notifyDateChanged(oldDate) }
                                // set the first period in the database
                                val last_period = selectedDate
                                if (last_period != null) {
                                    val last_period = last_period.toCalendar()
                                    Log.d("LASTPERIOD", "click on day ${last_period.formatDate()}")
                                    realm.setFirstPeriod(last_period, this@LastPeriodFragment.context)
                                    (this@LastPeriodFragment.activity as AppIntroActivity).setupComplete = true
                                }
                            }
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
                    when (day.date) {
                        selectedDate -> {
                            textView.setTextColorRes(R.color.white)
                            textView.setBackgroundResource(R.drawable.primary_selected_bg)
                        }
                        today -> {
                            textView.setTypeface(textView.typeface, Typeface.BOLD)
                            textView.background = null
                        }
                        else -> {
                            if (day.date.isAfter(today)) {
                                textView.setTextColorRes(R.color.secondaryText)
                                textView.background = null
                            } else {
                                textView.setTextColorRes(R.color.primaryText)
                                textView.background = null
                            }
                        }
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

    companion object {
        fun newInstance(): LastPeriodFragment {
            val fragment = LastPeriodFragment()
            val args = Bundle()
            fragment.arguments = args
            return fragment
        }
    }
}