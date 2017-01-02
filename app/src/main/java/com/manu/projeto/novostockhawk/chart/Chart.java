package com.manu.projeto.novostockhawk.chart;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import com.db.chart.Tools;
import com.db.chart.model.LineSet;
import com.db.chart.renderer.AxisRenderer;
import com.db.chart.view.BarChartView;
import com.db.chart.view.ChartView;
import com.db.chart.view.LineChartView;
import com.manu.projeto.novostockhawk.R;



import java.util.ArrayList;

/**
 * Created by emanu on 01/01/2017.
 */

public class Chart extends AppCompatActivity {

    private LineChartView mChart;
    private BarChartView barChartView;
    private TextView mDetailTextView;

    @Override
    protected void onResume() {
        super.onResume();

        setContentView(R.layout.grafico);

        mChart = (LineChartView) findViewById(R.id.linechart);
        mDetailTextView = (TextView) findViewById(R.id.tv_detail);

        if (getIntent() != null) {
            Bundle intentBundle = getIntent().getExtras();

            if (intentBundle == null ||
                    !intentBundle.containsKey("endValues") ||
                    !intentBundle.containsKey("dates")) {
                finish();
            } else {
                drawChart(intentBundle.getStringArrayList("dates"), intentBundle.getStringArrayList("endValues"));
            }
        } else {
            finish();
        }
    }

    private void drawChart(ArrayList<String> dates, ArrayList<String> endValues) {

        LineSet dataset = new LineSet();

        float minValue = Float.valueOf(endValues.get(0));
        float maxValue = 0.f;

        for (int i = 0; i < endValues.size(); i++) {
            String label = dates.get(i);
            float value = Float.valueOf(endValues.get(i));

            if (value > maxValue) {
                maxValue = value;
            }

            if (value < minValue) {
                minValue = value;
            }

            dataset.addPoint(label, value);
        }

        dataset.setColor(Color.parseColor("#ff0000"))
                .setThickness(4)
                .setSmooth(true);

        mChart.addData(dataset);

        Paint gridPaint = new Paint();
        gridPaint.setColor(Color.BLACK);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setAntiAlias(true);
        gridPaint.setStrokeWidth(Tools.fromDpToPx(.75f));

        mChart.setAxisThickness(3)
                .setAxisColor(Color.BLACK)
                .setAxisBorderValues(Math.round(minValue) - 1, Math.round(maxValue) + 1)
                .setLabelsColor(Color.BLACK)
                .setXLabels(AxisRenderer.LabelPosition.NONE)
                .setYLabels(AxisRenderer.LabelPosition.OUTSIDE)
                .setGrid(ChartView.GridType.HORIZONTAL, gridPaint)
                .setXAxis(true)
                .setYAxis(true);

        mChart.show();

        mDetailTextView.setText("Maior valor: " + maxValue + "\n" + "Menor valor: " + minValue);
    }
}

