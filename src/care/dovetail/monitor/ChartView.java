package care.dovetail.monitor;

import java.util.List;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.View;

public class ChartView extends View {

	public enum Type {
		LINE,
		POINT
	}

	private final Paint paint = new Paint();

	private Bitmap bitmap;
	private Canvas bitmapCanvas;

	private Type type;

	private Pair<Integer, Integer> minMaxX = Pair.create(-1, -1);
	private Pair<Integer, Integer> minMaxY = Pair.create(-1, -1);

	public ChartView(Context context, AttributeSet attrs) {
		super(context, attrs);
		paint.setAntiAlias(true);
		paint.setDither(true);
		paint.setStyle(Paint.Style.STROKE);
	}

	public void setType(Type type) {
		this.type = type;
		paint.setStyle(type == Type.POINT ? Paint.Style.FILL : Paint.Style.STROKE);
	}

	public void setColor(int color) {
		paint.setColor(color);
	}

	public void setThickness(int size) {
		paint.setStrokeWidth(size);
	}

	public void setXRange(int min, int max) {
		minMaxX = Pair.create(min, max);
	}

	public void setYRange(int min, int max) {
		minMaxY = Pair.create(min, max);
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		clearAll(w, h);
		super.onSizeChanged(w, h, oldw, oldh);
	}

	public void clearAll(int width, int height) {
		bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		bitmapCanvas = new Canvas();
		bitmapCanvas.setBitmap(bitmap);
		bitmapCanvas.drawColor(Color.TRANSPARENT);
	}

	@Override
	public void onDraw(Canvas canvas) {
		canvas.drawBitmap(bitmap, 0, 0, paint);
	}

	public void setData(List<Pair<Integer, Integer>> data) {
		clearAll(bitmap.getWidth(), bitmap.getHeight());

		if (minMaxX.first == -1 && minMaxX.second == -1) {
			minMaxX = getMinMax(data, true);
		}
		if (minMaxY.first == -1 && minMaxY.second == -1) {
			minMaxY = getMinMax(data, false);
		}

		if (data == null || data.size() == 0) {
			invalidate();
			return;
		}

		if (type == Type.POINT) {
			drawPoints(data);
		} else {
			drawPath(data);
		}

		invalidate();
	}

	private void drawPath(List<Pair<Integer, Integer>> data) {
		Path path = new Path();
		int lastX = getX(data.get(0).first, minMaxX.first, minMaxX.second);
		int lastY = getY(data.get(0).second, minMaxY.first, minMaxY.second);
		path.moveTo(lastX, lastY);
		for (int i = 1; i < data.size(); i++) {
			int x = getX(data.get(i).first, minMaxX.first, minMaxX.second);
			int y = getY(data.get(i).second, minMaxY.first, minMaxY.second);
			path.quadTo(lastX, lastY, x, y);
			lastX = x;
			lastY = y;
		}
		bitmapCanvas.drawPath(path, paint);
	}

	private void drawPoints(List<Pair<Integer, Integer>> data) {
		for (int i = 0; i < data.size(); i++) {
			int x = getX(data.get(i).first, minMaxX.first, minMaxX.second);
			int y = getY(data.get(i).second, minMaxY.first, minMaxY.second);
			bitmapCanvas.drawCircle(x, y, paint.getStrokeWidth(), paint);
		}
	}

	static private Pair<Integer, Integer> getMinMax(List<Pair<Integer, Integer>> data, boolean x) {
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;

		for (Pair<Integer, Integer> point : data) {
			min = Math.min(min, x ? point.first : point.second);
			max = Math.max(max, x ? point.first : point.second);
		}

		return Pair.create(min, max);
	}

	private int getX(int value, int min, int max) {
		return max <= min ? 0 : bitmap.getWidth() * (value - min) / (max - min);
	}

	private int getY(int value, int min, int max) {
		if (max <= min) {
			return 0;
		}
		return bitmap.getHeight() - (bitmap.getHeight() * (value - min) / (max - min));
	}
}
