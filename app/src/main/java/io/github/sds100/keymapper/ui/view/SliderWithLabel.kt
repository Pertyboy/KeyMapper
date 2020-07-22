package io.github.sds100.keymapper.ui.view

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.TextView
import androidx.databinding.BindingAdapter
import com.google.android.material.slider.Slider
import io.github.sds100.keymapper.R
import io.github.sds100.keymapper.data.model.SliderModel
import io.github.sds100.keymapper.util.int
import io.github.sds100.keymapper.util.str

/**
 * Created by sds100 on 04/06/20.
 */
class SliderWithLabel(context: Context,
                      attrs: AttributeSet?,
                      defStyleAttr: Int
) : FrameLayout(context, attrs, defStyleAttr) {

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context) : this(context, null, 0)

    private val mSlider by lazy { findViewById<Slider>(R.id.slider) }
    private val mSliderValue by lazy { findViewById<TextView>(R.id.textViewSliderValue) }

    private var mIsDefaultStepEnabled = false

    init {
        inflate(context, R.layout.slider_with_label, this)

        mSlider.addOnChangeListener { _, value, _ ->
            setSliderValueTextViewText(value)
        }

        mSlider.setLabelFormatter {
            //Set text to "default" if the slider is in the "default" step position.
            if (mIsDefaultStepEnabled && it == mSlider.valueFrom) {
                str(R.string.slider_default)
            } else {
                try {
                    it.toInt().toString()
                } catch (e: NumberFormatException) {
                    it.toString()
                }
            }
        }
    }

    fun applyModel(model: SliderModel) {
        val min = context.int(model.min)
        val max = context.int(model.max)
        val stepSize = context.int(model.stepSize)

        val defaultStepValue = calculateDefaultStepValue(min.toFloat(), stepSize.toFloat())

        mSlider.valueFrom = if (model.isDefaultStepEnabled) {
            defaultStepValue
        } else {
            min.toFloat()
        }

        mSlider.valueTo = max.toFloat()
        mSlider.stepSize = stepSize.toFloat()

        mIsDefaultStepEnabled = model.isDefaultStepEnabled

        if (model.value != null) {
            when {
                model.value > max -> mSlider.value = max.toFloat()
                model.value < min -> mSlider.value = min.toFloat()
                else -> mSlider.value = model.value.toFloat()
            }
        } else {
            mSlider.value = defaultStepValue
        }

        setSliderValueTextViewText(mSlider.value)
    }

    fun setListener(onChangeListener: Slider.OnChangeListener) {
        mSlider.clearOnChangeListeners()
        mSlider.addOnChangeListener(onChangeListener)
    }

    private fun calculateDefaultStepValue(min: Float, stepSize: Float): Float {
        return min - stepSize
    }

    private fun setSliderValueTextViewText(value: Float) {
        //Set text to "default" if the slider is in the "default" step position.
        if (mIsDefaultStepEnabled && value == mSlider.valueFrom) {
            mSliderValue.setText(R.string.slider_default)
        } else {
            val text = try {
                value.toInt().toString()
            } catch (e: NumberFormatException) {
                value.toString()
            }

            mSliderValue.text = text
        }
    }
}

@BindingAdapter("app:sliderModel")
fun SliderWithLabel.setModel(model: SliderModel) {
    applyModel(model)
}

@BindingAdapter("app:onChangeListener")
fun SliderWithLabel.setOnChangeListener(onChangeListener: Slider.OnChangeListener) {
    setListener(onChangeListener)
}
