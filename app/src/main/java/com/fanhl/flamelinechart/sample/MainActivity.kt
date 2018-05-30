package com.fanhl.flamelinechart.sample

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import com.lxt.cfmoto.chart.TravelChart
import com.lxt.cfmoto.chart.Vector2
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        chart_travel.dataParser = object : TravelChart.DataParser {
            override fun parseItem(item: Any): Vector2 {
                val itemItem = item as? Item ?: return Vector2(0f, 0f)

                return Vector2(itemItem.x, itemItem.y)
            }
        }
        chart_travel.data = TravelChart.Data<Item>().apply {
            list.apply {
                fun add(x: Float, y: Float) {
                    add(Item(x, y))
                }
                add(0f, 1f)
                add(100f, 200f)
                add(200f, 5f)
                add(300f, 400f)
                add(400f, 100f)
                add(500f, 20f)
            }
        }
    }

    data class Item(
            var x: Float,
            var y: Float
    ) : TravelChart.IItem {
        override fun getYAxies(): Float {
            return y
        }
    }
}
