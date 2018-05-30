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
            override fun parseItem(item: TravelChart.IItem): Vector2 {
                val itemItem = Vector2(item.getXAxis(), item.getYAxis())

                return Vector2(itemItem.x, itemItem.y)
            }
        }
        chart_travel.data = TravelChart.Data<Item>().apply {
            list.apply {
                fun add(x: Float, y: Float) {
                    add(Item(x, y))
                }
                add(0f, 1f)
                add(1f, 200f)
                add(2f, 5f)
                add(3f, 400f)
                add(4f, 100f)
                add(5f, 20f)
            }
        }
    }

    data class Item(
            var x: Float,
            var y: Float
    ) : TravelChart.IItem {
        override fun getXAxis(): Float {
            return x
        }

        override fun getYAxis(): Float {
            return y
        }
    }
}
