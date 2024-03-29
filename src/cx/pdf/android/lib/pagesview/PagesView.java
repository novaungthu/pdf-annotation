package cx.pdf.android.lib.pagesview;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import cx.pdf.android.pdfview.R;
import cx.pdf.android.pdfview.Actions;
import cx.pdf.android.pdfview.Bookmark;
import cx.pdf.android.pdfview.BookmarkEntry;
import cx.pdf.android.pdfview.Options;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.GestureDetector;
import android.view.GestureDetector.OnDoubleTapListener;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Scroller;

/**
 * View that simplifies displaying of paged documents.
 * TODO: redesign zooms, pages, margins, layout
 * TODO: use more floats for better align, or use more ints for performance ;) (that is, really analyse what should be used when)
 * PDF Annotation: 35% source code inserted
 * Date of last change: 20.04.2012
 */
public class PagesView extends View implements 
View.OnTouchListener, OnImageRenderedListener, View.OnKeyListener {
	/**
	 * Logging tag.
	 */
	private static final String TAG = "cx.pdf.android.pdfview";
	
	/* Experiments show that larger tiles are faster, but the gains do drop off,
	 * and must be balanced against the size of memory chunks being requested.
	 */
	private static final int MIN_TILE_WIDTH = 256;
	private static final int MAX_TILE_WIDTH = 640;
	private static final int MIN_TILE_HEIGHT = 128;
	private static final int MAX_TILE_PIXELS = 640*360;
	
//	private final static int MAX_ZOOM = 4000;
//	private final static int MIN_ZOOM = 100;
	
	/**
	 * Space between screen edge and page and between pages.
	 */
	private int MARGIN_X = 0;
	private static final int MARGIN_Y = 10;
	
	private long annotTime = 0;
	public boolean vibrate = true;
	private Cursor cannot = null;
	private int cposition = 0;
	private boolean writePosition = false, movePosition = false;
	private float changeAbleX = -1, changeAbleY = -1;
	private float changeAbleH = 0, changeAbleW = 0;
	private int realPageNumber = -1;
	
	/* zoom steps */
	float step = 1.414f;
	
	/* volume keys page */
	boolean pageWithVolume = true;
	
	private Activity activity = null;
	
	/**
	 * Source of page bitmaps.
	 */
	private PagesProvider pagesProvider = null;
	
	
	@SuppressWarnings("unused")
	private long lastControlsUseMillis = 0;
	
	private int colorMode;
	
	private float maxRealPageSize[] = {0f, 0f};
	private float realDocumentSize[] = {0f, 0f};
	
	/**
	 * Current width of this view.
	 */
	private int width = 0;
	
	/**
	 * Current height of this view.
	 */
	private int height = 0;
	
	/**
	 * Position over book, not counting drag.
	 * This is position of viewports center, not top-left corner. 
	 */
	private int left = 0;
	
	/**
	 * Position over book, not counting drag.
	 * This is position of viewports center, not top-left corner.
	 */
	private int top = 0;
	
	/**
	 * Current zoom level.
	 * 1000 is 100%.
	 */
	private int zoomLevel = 1000;
	
	/**
	 * Current rotation of pages.
	 */
	private int rotation = 0;
	
	/**
	 * Base scaling factor - how much shrink (or grow) page to fit it nicely to screen at zoomLevel = 1000.
	 * For example, if we determine that 200x400 image fits screen best, but PDF's pages are 400x800, then
	 * base scaling would be 0.5, since at base scaling, without any zoom, page should fit into screen nicely.
	 */
	private float scaling0 = 0f;
	
	/**
	 * Page sized obtained from pages provider.
	 * These do not change.
	 */
	private int pageSizes[][];
	
	/**
	 * Find mode.
	 */
	private boolean findMode = false;
	
	/**
	 * Annotation mode.
	 */
	public boolean annotMode = false;
	public int annotPosId = -1;
	public int annotResId = -1;

	/**
	 * Paint used to draw find results.
	 */
	private Paint findResultsPaint = null;
	
	/**
	 * Currently displayed find results.
	 */
	private List<FindResult> findResults = null;

	/**
	 * hold the currently displayed page 
	 */
	private int currentPage = 0;
	
	/**
	 * avoid too much allocations in rectsintersect()
	 */
	private static Rect r1 = new Rect();
	

	/**
	 * Bookmarked page to go to.
	 */
	private BookmarkEntry bookmarkToRestore = null;
	
	/**
	 * Construct this view.
	 * @param activity parent activity
	 */
	
	private boolean eink = false;
	private boolean volumeUpIsDown = false;
	private boolean volumeDownIsDown = false;	
	private GestureDetector gestureDetector = null;
	private Scroller scroller = null;
	
	private boolean verticalScrollLock = true;
	private boolean lockedVertically = false;
	private float downX = 0;
	private float downY = 0;
	private float lastX = 0;
	private float lastY = 0;
	private float maxExcursionY = 0;
	private int doubleTapAction = Options.DOUBLE_TAP_ZOOM_IN_OUT;
	private int zoomToRestore = 0;
	private int leftToRestore;
	private Actions actions = null;
	private boolean nook2 = false;
	
	public String fileName = null;
	
	public PagesView(final Activity activity) {
		super(activity);
		this.activity = activity;
		this.actions = null;
		this.lastControlsUseMillis = System.currentTimeMillis();
		this.findResultsPaint = new Paint();
		this.findResultsPaint.setARGB(0xd0, 0xc0, 0, 0);
		this.findResultsPaint.setStyle(Paint.Style.FILL);
		this.findResultsPaint.setAntiAlias(true);
		this.findResultsPaint.setStrokeWidth(3);
		this.setOnTouchListener(this);
		this.setOnKeyListener(this);
		activity.setDefaultKeyMode(Activity.DEFAULT_KEYS_SEARCH_LOCAL);
		
		this.scroller = null; // new Scroller(activity);
		
		this.gestureDetector = new GestureDetector(activity, 
				new GestureDetector.OnGestureListener() {
					public boolean onDown(MotionEvent e) {
						return false;
					}

					public boolean onFling(MotionEvent e1, MotionEvent e2,
							float velocityX, float velocityY) {
						
						if (lockedVertically) 
							velocityX = 0;
						
						if (!annotMode)
							doFling(velocityX, velocityY);
						return true;
					}

					/** PDF Annotation: Long press - action to view or insert an annotation */
					public void onLongPress(MotionEvent e) {
						Point pagePosition = getPagePositionOnScreen(0);
						float x = e.getX();
						float y = e.getY();
						float z = (scaling0 * (float) zoomLevel * 0.001f);
						float pagex = pagePosition.x;
						float pagey = pagePosition.y;
						
						// show annotation contents
						if (!annotMode && annotTime > 0) {
							// vibrate
							// ((cx.hell.android.pdfview.OpenFileActivity)activity).makeVibrate(75);
							
							annotTime = 0;
							if (cannot != null) {
								cannot.moveToPosition(cposition);
						        ((cx.pdf.android.pdfview.OpenFileActivity)activity).showAnnotText(cposition, cannot);
							}
							
					    // show annotation menu (add a new annotation)
						} else if (!annotMode)  {
							int realPageNo = 0;
							while ((-y + pagey + pagePosition(realPageNo)) < 0) {
								realPageNo++;
							}
							
							float lly = (-y + pagey + pagePosition(realPageNo));
							float llx = (x-pagex);
							float tmp = 0;
	
							// y margin position control, exception for page number 1
							if (realPageNo == 1) {
								if ((pagePosition(realPageNo) - ((float) MARGIN_Y * zoomLevel * 0.001f))
									- (tmp+getCurrentPageHeight(0) - ((float) MARGIN_Y * zoomLevel * 0.001f)) >= lly) {
									Log.i(TAG, "Annotation cannot insert! Space position.");
									return;
								}	
								
							// y margin position control, page number greater than 1
							} else {
								for (int i = 1; i < realPageNo; i++) {
									if ((pagePosition(i) - ((float) MARGIN_Y * zoomLevel * 0.001f))
										- (tmp+getCurrentPageHeight(i-1) - ((float) MARGIN_Y * zoomLevel * 0.001f)) >= lly) {
										Log.i(TAG, "Annotation cannot insert! Space position.");
										return;
									}
									tmp += pagePosition(i);
								}
							}
							
							// float position control
							if (llx < 0
								|| llx > getCurrentPageWidth(realPageNo-1 >= 0 ? realPageNo-1 : 0) 
								|| y < pagey) {
								Log.i(TAG, "Annotation cannot insert! Space position.");
								return;
							}
							
							float llyz  = ((-y + pagey + pagePosition(realPageNo))/z);
							float llxz = (x-pagex)/z;
							
							// vibrate
							// ((cx.hell.android.pdfview.OpenFileActivity)activity).makeVibrate(50);
							
							// insert new annotations dialog
							if (llyz > 0) {
								((cx.pdf.android.pdfview.OpenFileActivity)activity)
									.showAnnotDialog(llxz, llyz, realPageNo-1);
							// too up, must scroll down
							} else {
								((cx.pdf.android.pdfview.OpenFileActivity)activity).makeToast(R.string.scroll_down);
							}
						} 
					
					}
					
					public boolean onScroll(MotionEvent e1, MotionEvent e2,
							float distanceX, float distanceY) {
						return false;
					}

					public void onShowPress(MotionEvent e) {
					}

					public boolean onSingleTapUp(MotionEvent e) {
						return false;
					}
		});
		
		gestureDetector.setOnDoubleTapListener(new OnDoubleTapListener() {
			public boolean onDoubleTap(MotionEvent e) {
				switch(doubleTapAction) {
				case Options.DOUBLE_TAP_ZOOM_IN_OUT:
					if (zoomToRestore != 0) {
						left = leftToRestore;
						top = top * zoomToRestore / zoomLevel;
						zoomLevel = zoomToRestore;
						invalidate();
						zoomToRestore = 0;
					}
					else {
						int oldLeft = left;
						int oldZoom = zoomLevel;
						left += e.getX() - width/2;
						top += e.getY() - height/2;
						zoom(2f);
						zoomToRestore = oldZoom;
						leftToRestore = oldLeft;
					}
					return false;
				case Options.DOUBLE_TAP_ZOOM:
					left += e.getX() - width/2;
					top += e.getY() - height/2;
					zoom(2f);
					return false;
				default:
					return false;
				}
			}

			public boolean onDoubleTapEvent(MotionEvent e) {
				return false;
			}

			public boolean onSingleTapConfirmed(MotionEvent e) {				
				return false;
			}});
	}
		
	public void setStartBookmark(Bookmark b, String bookmarkName) {
		if (b != null) {
			this.bookmarkToRestore = b.getLast(bookmarkName);
			
			if (this.bookmarkToRestore == null)
				return;
						
			if (this.bookmarkToRestore.numberOfPages != this.pageSizes.length) {
				this.bookmarkToRestore = null;
				return;
			}

			if (0<this.bookmarkToRestore.page) {
				this.currentPage = this.bookmarkToRestore.page;
			}
		}
	}
	
	public void setFilePath (String file) {
		this.fileName = file;
	}
		
	/**
	 * Handle size change event.
	 * Update base scaling, move zoom controls to correct place etc.
	 * @param w new width
	 * @param h new height
	 * @param oldw old width
	 * @param oldh old height
	 */
	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		this.width = w;
		this.height = h;
		if (this.scaling0 == 0f) {
			this.scaling0 = Math.min(
					((float)this.height - 2*MARGIN_Y) / (float)this.pageSizes[0][1],
					((float)this.width - 2*MARGIN_X) / (float)this.pageSizes[0][0]);
		}
		if (oldw == 0 && oldh == 0) {
			goToBookmark();
		}
	}
	
	public void goToBookmark() {

		if (this.bookmarkToRestore == null) {
			this.top  = this.height / 2;
			this.left = this.width / 2;
		}
		else {
			this.zoomLevel = (int)(this.bookmarkToRestore.absoluteZoomLevel / this.scaling0);
			this.rotation = this.bookmarkToRestore.rotation;
			Point pos = getPagePositionInDocumentWithZoom(this.bookmarkToRestore.page);
			this.currentPage = this.bookmarkToRestore.page;
			this.top = pos.y + this.height / 2;
			this.left = this.getCurrentPageWidth(this.currentPage)/2 + MARGIN_X + this.bookmarkToRestore.offsetX;
			this.bookmarkToRestore = null;
		}
	}
	
	public void setPagesProvider(PagesProvider pagesProvider) {
		this.pagesProvider = pagesProvider;
		if (this.pagesProvider != null) {
			this.pageSizes = this.pagesProvider.getPageSizes();
			
			maxRealPageSize[0] = 0f;
			maxRealPageSize[1] = 0f;
			realDocumentSize[0] = 0f;
			realDocumentSize[1] = 0f;
			
			for (int i = 0; i < this.pageSizes.length; i++) 
				for (int j = 0; j<2; j++) {
					if (pageSizes[i][j] > maxRealPageSize[j])
						maxRealPageSize[j] = pageSizes[i][j];
					realDocumentSize[j] += pageSizes[i][j]; 
				}
			
			if (this.width > 0 && this.height > 0) {
				this.scaling0 = Math.min(
						((float)this.height - 2*MARGIN_Y) / (float)this.pageSizes[0][1],
						((float)this.width - 2*MARGIN_X) / (float)this.pageSizes[0][0]);
				this.left = this.width / 2;
				this.top = this.height / 2;

			}
		} else {
			this.pageSizes = null;
		}
		this.pagesProvider.setOnImageRenderedListener(this);
	}
	
	/**
	 * Draw view.
	 * @param canvas what to draw on
	 */
	
	int prevTop = -1;
	int prevLeft = -1;
	
	public void onDraw(Canvas canvas) {
		if (this.nook2) {
			N2EpdController.setGL16Mode();
		}
		this.drawPages(canvas);
		if (this.findMode) this.drawFindResults(canvas);
		this.drawAnnots(canvas); // draw annotation icons
	}
	
	/***** START OF ANNOTATIONS DRAWING *****/
	
	/**
	 * Get number of suptype.
	 * @param subtype subtype string
	 * @return number of subtype
	 */
	private int getSubtypeNum (String subtype) {
		int number = 0;
		
		try {
			if (subtype.equalsIgnoreCase("Text")) {
				number = 0;
			} else if (subtype.equalsIgnoreCase("Circle")) {
				number = 1;
			} else if (subtype.equalsIgnoreCase("Square")) {
				number = 2;
			}
			
		} catch (Exception e) {
			Log.e(TAG, "Get subtype of annotation: " + e);
		} 
		
		return number; 
	}
	
	/**
	 * Draw annotations page to page
	 * @param canvas
	 */
	public void drawAnnots(Canvas canvas) {
		int realPageNo = getCurrentPage(), cpage = 0;
		int screenSize = (int) (this.height / (this.zoomLevel * 0.001f));
		// compute number of last page on screen
		while (screenSize > 0) {
			realPageNo++;
			if (realPageNo < this.getPageCount())
				screenSize -= getCurrentPageHeight(realPageNo);
			else
				break;
		}
		
		try {
			// select file annotation from database
			Cursor cursor = ((cx.pdf.android.pdfview.OpenFileActivity)activity).getAnnotsFromSQL(-1);
			if (cursor != null && cursor.getCount() > 0) {
				cursor.moveToFirst();
				do {
					cpage = cursor.getInt(cursor.getColumnIndex("page"))-1;
					
					// exist annotations on pages on screen?
					if (cpage >= getCurrentPage() && cpage <= realPageNo) {
						// draw annotation
						drawAnnotsPage(canvas, cursor, cursor.getPosition());
					}
					
				} while (cursor.moveToNext());
				
			} else {
				// Log.w(TAG, "No annotation found! ");	
			}
		} catch (Exception e) {
			Log.e(TAG, "Loading page annotation error: " + e);
		}
		
		// search interval
		// Log.i(TAG, "<" + getCurrentPage() + ";" + realPageNo + "> ");
		
	}
	
	/**
	 * Draw annotation icon (by type) to PDF screen
	 * @param canvas actual host to draw call
	 */
	public void drawAnnotsPage(Canvas canvas, Cursor cursor, int position) {
		cursor.moveToPosition(position);
		int pageno = cursor.getInt(cursor.getColumnIndex("page"))-1;
		Point pagePosition = this.getPagePositionOnScreen(pageno);
		//Cursor appearance = null;
		float llx = 0, lly = 0, urx = 0, ury = 0;
		float pagex = pagePosition.x, pagey = pagePosition.y;
		String subtype = null;
		// bitmaps represents annotations
		Bitmap flag_red = BitmapFactory.decodeResource(getResources(), R.drawable.icon_flag_red);
		Bitmap flag_blue = BitmapFactory.decodeResource(getResources(), R.drawable.icon_flag_blue);
		Bitmap flag_add = BitmapFactory.decodeResource(getResources(), R.drawable.icon_flag_add);
		Bitmap icon_move = BitmapFactory.decodeResource(getResources(), R.drawable.icon_move);
		Bitmap icon_resize = BitmapFactory.decodeResource(getResources(), R.drawable.icon_resize);
		Bitmap default_flag = flag_blue;
				
		float z = (this.scaling0 * (float)this.zoomLevel * 0.001f);
		int fsize = (int) (10 * z), asize = (int) (22 * z), flag = 0, bdweight = 0;
		
		// flag size controller
		if (fsize < 1) return;
		
	
		try {
			llx = cursor.getFloat(cursor.getColumnIndex("llx"));
			lly = cursor.getFloat(cursor.getColumnIndex("lly"));
			urx = cursor.getFloat(cursor.getColumnIndex("urx"));
			ury = cursor.getFloat(cursor.getColumnIndex("ury"));
			bdweight = cursor.getInt(cursor.getColumnIndex("bdweight"));
			            
			            // annotation flag icon
			            flag = cursor.getInt(cursor.getColumnIndex("flag"));
			            switch (flag) {
			            	case 0: default_flag = flag_blue; break;   // flag(0) original
			            	case 1: default_flag = flag_red; break;    // flag(1) modified
			            	case 2: default_flag = flag_add; break;    // flag(2) add new
			            }
			            subtype = cursor.getString(cursor.getColumnIndex("subtype"));
			            
			            
			            float llxz = llx * z + pagex;
			            float urxz = urx * z + pagex;
			            float llyz = (getCurrentPageHeight(pageno) - (lly * z)) + pagey;
		            	float uryz = (getCurrentPageHeight(pageno) - (ury * z)) + pagey;
		           		     
		            	// calculate position and size of annotation
			            if (!movePosition) {
			            	if (annotPosId > -1) {
				            	changeAbleW = urxz - llxz ;
				            	changeAbleH = - (uryz - llyz);
			            		changeAbleX = llxz;
			            		changeAbleY = uryz;
			            	} else {
			            		changeAbleW = getActSize(llx, urx, z);
				            	changeAbleH = getActSize(lly, ury, z);
			            		changeAbleX = urxz;
			            		changeAbleY = uryz;
			            	}
		            	} else {
		            		
		            	}
		            	
			            // flag icon
			            Bitmap fresized = android.graphics.Bitmap.createScaledBitmap(default_flag, fsize, fsize, true);

			            // draw annotations without flag(3) deleted
			            if (flag != 3) {
			            	int ri = getDrawableAnnotIcon(cursor.getString(cursor.getColumnIndex("type")));
			            	Bitmap annot_icon = BitmapFactory.decodeResource(getResources(), ri);
				            Bitmap aresized = android.graphics.Bitmap.createScaledBitmap(annot_icon, asize, asize, true);
				            
				            // set paint
				            Paint paintStroke = setPaint(cursor, Paint.Style.STROKE, "color", bdweight, 0);
				            Paint paintFill = null, editPaint = null;
				            // load appearance from database
			        		/*appearance = ((cx.pdf.android.pdfview.OpenFileActivity)activity)
			        			.getAnnotsFromSQL(cursor.getInt(cursor.getColumnIndex("objectid")));
				            if (appearance != null && appearance.moveToFirst()) {
					            paintFill = setPaint(appearance, Paint.Style.FILL, "bgcolor", bdweight, 0);				            
				            }*/

				            
				            RectF oval = new RectF();
				            Point point = new Point();
				            
				            if (cursor.getInt(cursor.getColumnIndex("_id")) == this.annotPosId && this.annotMode) {
				            	switch (getSubtypeNum(subtype)) {
					            case 0: // text annotation
					            	editPaint = setPaint(null, Paint.Style.STROKE, null, bdweight, 2);
					            	break; 
					            case 1: // circle annotation
					            	editPaint = setPaint(null, Paint.Style.STROKE, null, bdweight, 2);
					            	break;
					            case 2: // square annotation
					            	editPaint = setPaint(null, Paint.Style.STROKE, null, bdweight, 
						            	(cursor.getString(cursor.getColumnIndex("color")).equalsIgnoreCase("ffffffff")) ? 2 : 1);
					            	break;
				            	}	            	
			            	
				            	editPaint = setPaint(null, Paint.Style.STROKE, null, bdweight, 
				            		(cursor.getString(cursor.getColumnIndex("color")).equalsIgnoreCase("ffffffff")) ? 2 : 1);
				            	oval.set(changeAbleX, changeAbleY, changeAbleX + changeAbleW, changeAbleY + changeAbleH);
				            	point.set((int) changeAbleX, (int) changeAbleY);
				            	
 	
				            } else if (cursor.getInt(cursor.getColumnIndex("_id")) == this.annotResId && this.annotMode) {
				            	editPaint = setPaint(null, Paint.Style.STROKE, null, bdweight, 
				            		(cursor.getString(cursor.getColumnIndex("color")).equalsIgnoreCase("ffffffff")) ? 2 : 1);
				            	oval.set(llxz, llyz, getMinSize(llxz, changeAbleX, fsize), getMinSize(llyz, changeAbleY, fsize));

				            	lastX = getMinSize(llxz, changeAbleX, fsize);
				            	lastY = getMinSize(llyz, changeAbleY, fsize);
				            	
				            	// draw flag icon
				            	icon_resize = android.graphics.Bitmap.createScaledBitmap(icon_resize, fsize, fsize, true);
					            Matrix matrix = new Matrix();
					            matrix.postScale(fsize/icon_resize.getWidth(), fsize/icon_resize.getHeight());
					            matrix.postRotate(getRotate(llxz, llyz, changeAbleX, changeAbleY));
					            icon_resize = android.graphics.Bitmap.createBitmap(icon_resize, 0, 0, fsize, fsize, matrix, true);
				            	canvas.drawBitmap(icon_resize, 
				            			llxz < changeAbleX ? (changeAbleX - fsize) : (changeAbleX), 
				            			llyz < changeAbleY ? (changeAbleY - fsize) : (changeAbleY), 
				            			null);

				            } else {
				            	oval.set(llxz, uryz, urxz, llyz);
				            	point.set((int) (urxz > llxz ? llxz : urxz), (int) (uryz > llyz ? llyz : uryz));
				            }
				            float pi = (float) 3.14;
				            // draw new annotation icon
				            switch (getSubtypeNum(subtype)) {
				            case 0: // text annotation
				            	
				     			// TODO:
				            	paintFill = setPaint(cursor, Paint.Style.FILL, "color", bdweight, 0);
				            	if (paintFill != null)
				            		canvas.drawRoundRect(oval, pi * z, pi * z, paintFill);
						        
					            canvas.drawBitmap(aresized, point.x, point.y, null);
					           /* if (editPaint != null) 
					            	canvas.drawRect(oval, editPaint);
					             else */
					            	
					            
				            	if (cursor.getInt(cursor.getColumnIndex("_id")) == this.annotPosId && this.annotMode) { 
				            		Bitmap moved = android.graphics.Bitmap.createScaledBitmap(icon_move, fsize, fsize, true);
					            	canvas.drawBitmap(moved, changeAbleX + changeAbleW/2 - fsize/2, 
					            	changeAbleY + changeAbleH/2 - fsize/2, null);
				            	} else {
				            		canvas.drawBitmap(fresized, point.x, point.y, null);
				            	}
					            break;
				            case 1: // circle annotation
				            	/*if (paintFill != null) {
				            		canvas.drawOval(oval, paintFill);
				            	}*/
				            	if (paintStroke != null) {
				            		canvas.drawOval(oval, paintStroke);
				            	}
				            	/*if (editPaint != null)
					            	canvas.drawRect(oval, editPaint);
				            	else*/
					            	//canvas.drawBitmap(fresized, point.x, point.y, null);
				            	if (cursor.getInt(cursor.getColumnIndex("_id")) == this.annotPosId && this.annotMode) { 
				            		Bitmap moved = android.graphics.Bitmap.createScaledBitmap(icon_move, fsize, fsize, true);
					            	canvas.drawBitmap(moved, changeAbleX + changeAbleW/2 - fsize/2, 
					            	changeAbleY + changeAbleH/2 - fsize/2, null);
				            	} else if (cursor.getInt(cursor.getColumnIndex("_id")) == this.annotResId && this.annotMode) { 
				            		
				            	} else {
				            		canvas.drawBitmap(fresized, point.x, point.y, null);
				            	}
				            	break;
				            case 2: // square annotation
				            	/*if (paintFill != null)
				            		canvas.drawRect(oval, paintFill);*/
				            	if (paintStroke != null)
				            		canvas.drawRect(oval, paintStroke);
				            	/*if (editPaint != null)
					            	canvas.drawRect(oval, editPaint);
				            	else*/
					            	//canvas.drawBitmap(fresized, point.x, point.y, null);
				            	if (cursor.getInt(cursor.getColumnIndex("_id")) == this.annotPosId && this.annotMode) { 
				            		Bitmap moved = android.graphics.Bitmap.createScaledBitmap(icon_move, fsize, fsize, true);
					            	canvas.drawBitmap(moved, changeAbleX + changeAbleW/2 - fsize/2, 
					            	changeAbleY + changeAbleH/2 - fsize/2, null);
				            	} else if (cursor.getInt(cursor.getColumnIndex("_id")) == this.annotResId && this.annotMode) { 

				            	} else {
				            		canvas.drawBitmap(fresized, point.x, point.y, null);
				            	}
				            	
				            	break;
				            }
				           
			            } /*else {
			            	// draw flag icon
			            	canvas.drawBitmap(fresized, llxz, uryz + fsize, null);			            	 
			            }*/
			 
		} catch (IllegalStateException e) {
			Log.e(TAG, "Illegal State Exception: " + e);
		}
	}
	
	/**
	 * Get rotation by x and y position
	 * @param llx low left x
	 * @param lly low left y
	 * @param urx upper right x
	 * @param ury upper right y
	 * @return degrees of rotation
	 */
	private int getRotate (float llx, float lly, float urx, float ury) {
		int rotate = 0;
        rotate += llx < urx ? 0 : 90;
    	rotate += lly < ury ? 0 : -90;
    	rotate += llx > urx && lly > ury ? 180 : 0;	
    	return rotate;
	}
	
	/**
	 * Get minimal width and height of annotation (type square and circle)
	 * @param point source position
	 * @param length destination position
	 * @param size minimal size of annotation
	 * @return destination position
	 */
	private float getMinSize (float point, float length, float size) {
		if (point - length > -size && point - length < size) {
			return ((point - length > -size) && (point - length < 0)) ? (point + size) : (point - size);
		} else {
			return length;
		}
		
	}
	
	/**
	 * Return real size of annotation icon from PDF document
	 * @param ll low left position
	 * @param ur up right position
	 * @param z zoom level
	 * @return real size
	 */
	private float getActSize (float ll, float ur, float z) {
		return (ur * z) > (ll * z) ? ((ur * z) - (ll * z)) : ((ll * z) - (ur * z));
	}
	
	/**
	 * Set paint to draw annotation.
	 * @param cursor database cursor
	 * @param style line style
	 * @param color annotation color
	 * @param bdweight border weight
	 * @param mode draw mode (default(0) color, white(1) or red(2))
	 * @return
	 */
	private Paint setPaint (Cursor cursor, Paint.Style style, String color, int bdweight, int mode) {
		float z = (this.scaling0 * (float)this.zoomLevel * 0.001f);
		
		// no color, transparent
		if (cursor != null && cursor.getString(cursor.getColumnIndex(color)).equalsIgnoreCase("transparent")) {
        	return null;
        }
		
		Paint paint = new Paint();
        paint.setAntiAlias(true); 
        paint.setStyle(style);
        switch (mode) {
        case 0 :
        	paint.setColor(Color.parseColor("#" + cursor.getString(cursor.getColumnIndex(color))));
        	break;
        case 1 :
        	paint.setColor(Color.WHITE);
        	paint.setPathEffect(new DashPathEffect(new float[] {5, 10}, 0));
        	break;
        case 2 :
        	paint.setColor(Color.RED);
        	paint.setPathEffect(new DashPathEffect(new float[] {5, 10}, 0));
        	break;
        }

        paint.setStrokeWidth(bdweight*z);
        
        return paint;
	}
	
	/**
	 * Get annotation icon by type.
	 * @param type type of annotation
	 * @return reference to annotation icon
	 */
	private int getDrawableAnnotIcon (String type) {
		int icon = 0;
		// Cannot switch on a value of type String for source level below 1.7
    	if (type.equalsIgnoreCase("comment")) {
    		icon = R.drawable.icon_comment;
    	} else if (type.equalsIgnoreCase("key")) {
    		icon = R.drawable.icon_key;
    	} else if (type.equalsIgnoreCase("note")) {
    		icon = R.drawable.icon_note;
    	} else if (type.equalsIgnoreCase("help")) {
    		icon = R.drawable.icon_help;
    	} else if (type.equalsIgnoreCase("newParagraph")) {
    		icon = R.drawable.icon_new_paragraph;
    	} else if (type.equalsIgnoreCase("paragraph")) {
    		icon = R.drawable.icon_paragraph;
    	} else if (type.equalsIgnoreCase("insert")) {
    		icon = R.drawable.icon_insert;
    	}
    	return icon;
	}
	
	/***** END OF ANNOTATIONS DRAWING *****/
	
	/**
	 * Get current maximum page width by page number taking into account zoom and rotation
	 */
	private int getCurrentMaxPageWidth() {
		float realpagewidth = this.maxRealPageSize[this.rotation % 2 == 0 ? 0 : 1];
		return (int)(realpagewidth * scaling0 * (this.zoomLevel*0.001f));
	}
	
	/**
	 * Get current maximum page height by page number taking into account zoom and rotation
	 */
	/*private int getCurrentMaxPageHeight() {
		float realpageheight = this.maxRealPageSize[this.rotation % 2 == 0 ? 1 : 0];
		return (int)(realpageheight * scaling0 * (this.zoomLevel*0.001f));
	} */
	
	/**
	 * Get current maximum page width by page number taking into account zoom and rotation
	 */
	private int getCurrentDocumentHeight() {
		float realheight = this.realDocumentSize[this.rotation % 2 == 0 ? 1 : 0];
		/* we add pageSizes.length to account for round-off issues */
		return (int)(realheight * scaling0 * (this.zoomLevel*0.001f) +  
			(pageSizes.length - 1) * this.getCurrentMarginY());
	}
	
	/**
	 * Get current page width by page number taking into account zoom and rotation
	 * @param pageno 0-based page number
	 */
	private int getCurrentPageWidth(int pageno) {
		float realpagewidth = (float)this.pageSizes[pageno][this.rotation % 2 == 0 ? 0 : 1];
		float currentpagewidth = realpagewidth * scaling0 * (this.zoomLevel*0.001f);
		return (int)currentpagewidth;
	}
	
	/**
	 * Get current page height by page number taking into account zoom and rotation.
	 * @param pageno 0-based page number
	 */
	private float getCurrentPageHeight(int pageno) {
		float currentpageheight = 0.f;
		try {
			float realpageheight = (float)this.pageSizes[pageno][this.rotation % 2 == 0 ? 1 : 0];
			currentpageheight = realpageheight * scaling0 * (this.zoomLevel*0.001f);
		} catch (Exception e) {
			Log.w(TAG, "Current page height, page: " + pageno + " " + e);
		}
		
		return currentpageheight;
	}
	
	private float getCurrentMarginX() {
		return (float)MARGIN_X * this.zoomLevel * 0.001f;
	}
	
	private float getCurrentMarginY() {
		return (float)MARGIN_Y * this.zoomLevel * 0.001f;
	}
	
	/**
	 * This takes into account zoom level.
	 */
	private Point getPagePositionInDocumentWithZoom(int page) {
		float marginX = this.getCurrentMarginX();
		float marginY = this.getCurrentMarginY();
		float left = marginX;
		float top = 0;
		for(int i = 0; i < page; ++i) {
			top += this.getCurrentPageHeight(i);
		}
		top += (page+1) * marginY;
		
		return new Point((int)left, (int)top);
	}
	
	/**
	 * Calculate screens (viewports) top-left corner position over document.
	 */
	private Point getScreenPositionOverDocument() {
		float top = this.top - this.height / 2;
		float left = this.left - this.width / 2;
		return new Point((int)left, (int)top);
	}
	
	/**
	 * Calculate current page position on screen in pixels.
	 * @param page base-0 page number
	 */
	private Point getPagePositionOnScreen(int page) {
		if (page < 0) throw new IllegalArgumentException("page must be >= 0: " + page);
		if (page >= this.pageSizes.length) throw new IllegalArgumentException("page number too big: " + page);
		
		Point pagePositionInDocument = this.getPagePositionInDocumentWithZoom(page);
		Point screenPositionInDocument = this.getScreenPositionOverDocument();
		
		return new Point(
					pagePositionInDocument.x - screenPositionInDocument.x,
					pagePositionInDocument.y - screenPositionInDocument.y
				);
	}
	
	@Override
	public void computeScroll() {
		if (this.scroller == null) 
			return;
		
		if (this.scroller.computeScrollOffset()) {
			left = this.scroller.getCurrX();
			top = this.scroller.getCurrY();
			((cx.pdf.android.pdfview.OpenFileActivity)activity).showPageNumber(false);
			postInvalidate();
		}
	}
	
	/**
	 * Draw pages.
	 * Also collect info what's visible and push this info to page renderer.
	 */
	private void drawPages(Canvas canvas) {
		if (this.eink) {
			canvas.drawColor(Color.WHITE);
		}
		
		Rect src = new Rect(); /* TODO: move out of drawPages */
		Rect dst = new Rect(); /* TODO: move out of drawPages */
		int pageWidth = 0;
		int pageHeight = 0;
		float pagex0, pagey0, pagex1, pagey1; // in doc, counts zoom
		int x, y; // on screen
		int viewx0, viewy0; // view over doc
		LinkedList<Tile> visibleTiles = new LinkedList<Tile>();
		float currentMarginX = this.getCurrentMarginX();
		float currentMarginY = this.getCurrentMarginY();
		float renderAhead = this.pagesProvider.getRenderAhead();
		
		if (this.pagesProvider != null) {
			viewx0 = left - width/2;
			viewy0 = top - height/2;
			
			int pageCount = this.pageSizes.length;
			
			/* We now adjust the position to make sure we don't scroll too
			 * far away from the document text.
			 */
			int oldviewx0 = viewx0;
			int oldviewy0 = viewy0;
			
			viewx0 = adjustPosition(viewx0, width, (int)currentMarginX, 
					getCurrentMaxPageWidth());
			viewy0 = adjustPosition(viewy0, height, (int)currentMarginY,
					(int)getCurrentDocumentHeight());
			
			left += viewx0 - oldviewx0;
			top += viewy0 - oldviewy0;
			
			float currpageoff = currentMarginY;
			
			this.currentPage = -1;
			
			pagey0 = 0;
			int[] tileSizes = new int[2];
			
			for(int i = 0; i < pageCount; ++i) {
				// is page i visible?

				pageWidth = this.getCurrentPageWidth(i);
				pageHeight = (int) this.getCurrentPageHeight(i);
				
				pagex0 = currentMarginX;
				pagex1 = (int)(currentMarginX + pageWidth);
				pagey0 = currpageoff;
				pagey1 = (int)(currpageoff + pageHeight);
				
				if (rectsintersect(
							(int)pagex0, (int)pagey0, (int)pagex1, (int)pagey1, // page rect in doc
							viewx0, viewy0, viewx0 + this.width, 
							viewy0 + (int)(renderAhead*this.height) // viewport rect in doc, or close enough to it 
						))
				{
					if (this.currentPage == -1)  {
						// remember the currently displayed page
						this.currentPage = i;
					}
					
					x = (int)pagex0 - viewx0;
					y = (int)pagey0 - viewy0;
					
					getGoodTileSizes(tileSizes, pageWidth, pageHeight);
					for(int tileix = 0; tileix < (pageWidth + tileSizes[0]-1) / tileSizes[0]; ++tileix)
						for(int tileiy = 0; tileiy < (pageHeight + tileSizes[1]-1) / tileSizes[1]; ++tileiy) {
							
							dst.left = (int)(x + tileix*tileSizes[0]);
							dst.top = (int)(y + tileiy*tileSizes[1]);
							dst.right = dst.left + tileSizes[0];
							dst.bottom = dst.top + tileSizes[1];	
						
							if (dst.intersects(0, 0, this.width, (int)(renderAhead*this.height))) {

								Tile tile = new Tile(i, (int)(this.zoomLevel * scaling0), 
										tileix*tileSizes[0], tileiy*tileSizes[1], this.rotation,
										tileSizes[0], tileSizes[1]);
								if (dst.intersects(0, 0, this.width, this.height)) {
									Bitmap b = this.pagesProvider.getPageBitmap(tile);
									if (b != null) {
										//Log.d(TAG, "  have bitmap: " + b + ", size: " + b.getWidth() + " x " + b.getHeight());
										src.left = 0;
										src.top = 0;
										src.right = b.getWidth();
										src.bottom = b.getHeight();
										
										if (dst.right > x + pageWidth) {
											src.right = (int)(b.getWidth() * (float)((x+pageWidth)-dst.left) / (float)(dst.right - dst.left));
											dst.right = (int)(x + pageWidth);
										}
										
										if (dst.bottom > y + pageHeight) {
											src.bottom = (int)(b.getHeight() * (float)((y+pageHeight)-dst.top) / (float)(dst.bottom - dst.top));
											dst.bottom = (int)(y + pageHeight);
										}
										
										drawBitmap(canvas, b, src, dst);
										
									}
								}
								visibleTiles.add(tile);
							}
						}
				}
				
				
				/* move to next page */
				currpageoff += currentMarginY + this.getCurrentPageHeight(i);
			}
			this.pagesProvider.setVisibleTiles(visibleTiles);
		}
	}
		
	private void drawBitmap(Canvas canvas, Bitmap b, Rect src, Rect dst) {
		if (colorMode != Options.COLOR_MODE_NORMAL) {
			Paint paint = new Paint();
			Bitmap out;
			
			if (b.getConfig() == Bitmap.Config.ALPHA_8) {
				out = b.copy(Bitmap.Config.ARGB_8888, false);
			}
			else {
				out = b;
			}
			
			paint.setColorFilter(new 
					ColorMatrixColorFilter(new ColorMatrix(
							Options.getColorModeMatrix(this.colorMode))));

			canvas.drawBitmap(out, src, dst, paint);
			
			if (b.getConfig() == Bitmap.Config.ALPHA_8) {
				out.recycle();
			}
		}
		else {
			canvas.drawBitmap(b, src, dst, null);
		}
	}

	/**
	 * Draw find results.
	 * TODO prettier icons
	 * TODO message if nothing was found
	 * @param canvas drawing target
	 */
	private void drawFindResults(Canvas canvas) {
		if (!this.findMode) throw new RuntimeException("drawFindResults but not in find results mode");
		if (this.findResults == null || this.findResults.isEmpty()) {
			Log.w(TAG, "nothing found");
			return;
		}
		for(FindResult findResult: this.findResults) {
			if (findResult.markers == null || findResult.markers.isEmpty())
				throw new RuntimeException("illegal FindResult: find result must have at least one marker");
			Iterator<Rect> i = findResult.markers.iterator();
			Rect r = null;
			Point pagePosition = this.getPagePositionOnScreen(findResult.page);
			
			float pagex = pagePosition.x;
			float pagey = pagePosition.y;
			float z = (this.scaling0 * (float)this.zoomLevel * 0.001f);
			
			while(i.hasNext()) {

				r = i.next();
				canvas.drawLine(
						r.left * z + pagex, r.top * z + pagey,
						r.left * z + pagex, r.bottom * z + pagey,
						this.findResultsPaint);
				canvas.drawLine(
						r.left * z + pagex, r.bottom * z + pagey,
						r.right * z + pagex, r.bottom * z + pagey,
						this.findResultsPaint);
				canvas.drawLine(
						r.right * z + pagex, r.bottom * z + pagey,
						r.right * z + pagex, r.top * z + pagey,
						this.findResultsPaint);
//			canvas.drawRect(
//					r.left * z + pagex,
//					r.top * z + pagey,
//					r.right * z + pagex,
//					r.bottom * z + pagey,
//					this.findResultsPaint);
			Log.d(TAG, "marker lands on: " +
					(r.left * z + pagex) + ", " +
					(r.top * z + pagey) + ", " + 
					(r.right * z + pagex) + ", " +
					(r.bottom * z + pagey) + ", ");
			}
		}
	}

	private boolean unlocksVerticalLock(MotionEvent e) {
		float dx;
		float dy;
		
		dx = Math.abs(e.getX()-downX);
		dy = Math.abs(e.getY()-downY);
		
		if (dy > 0.25 * dx || maxExcursionY > 0.8 * dx)
			return false;
		
		return dx > width/5 || dx > height/5;
	}
	
	/***** START OF ANNOTATIONS TOUCH ACTIONS *****/
	
	/**
	 * Update new annotations position or size after mouse action up.
	 * @param y position y of mouse cursor
	 * @param pagex page position x
	 * @param pagey page position y
	 * @param z zoom level
	 */
	private void updateActionUp (MotionEvent event, float pagex, float pagey, float z) {
		if (writePosition) {
			
			// calculate real page number
			int realPageNo = 0;
			while (-event.getY() + pagey + pagePosition(realPageNo) < 0) {
				realPageNo++;
			}

			float dx = (event.getX()-pagex-changeAbleW/2)/z;
			float dy = (-event.getY() + pagey + pagePosition(realPageNo) - changeAbleH/2)/z - (MARGIN_Y)/this.scaling0;

			if (realPageNo == realPageNumber) {
				// update position of annotation
				if (annotPosId > -1) {
					((cx.pdf.android.pdfview.OpenFileActivity)activity).saveNewAnnotPos(
						this.annotPosId, dx, dy, 0);

				// update size of annotation (circle or square only)
				} else {
					((cx.pdf.android.pdfview.OpenFileActivity)activity).saveNewAnnotPos(
						this.annotResId, (lastX-pagex)/z, ((-lastY + pagey + pagePosition(realPageNo))/z - (MARGIN_Y)/this.scaling0), 2);
				}
			}
			
			movePosition = false;
			invalidate(); //!!!!!
		}
	}
	
	/**
	 * Calculate position and size of changeable annotation.
	 * @param x position x of mouse cursor
	 * @param y position y of mouse cursor
	 * @param z zoom level
	 */
	private void updateActionMove (float x, float y, float z) {
		Cursor cursor = null;
		// change position
		if (annotPosId > -1) {
			cursor = ((cx.pdf.android.pdfview.OpenFileActivity)activity).getAnnotById(annotPosId);
			if (cursor != null) {
				if  (cursor.moveToFirst()) {				        
					changeAbleW = getActSize(cursor.getFloat(cursor.getColumnIndex("llx")), cursor.getFloat(cursor.getColumnIndex("urx")), z);
				    changeAbleH = getActSize(cursor.getFloat(cursor.getColumnIndex("lly")), cursor.getFloat(cursor.getColumnIndex("ury")), z);
				}
			}
			changeAbleX = x - changeAbleW/2;
			changeAbleY = y - changeAbleH/2;
				
		// change size
		} else {
			changeAbleX = x;
			changeAbleY = y;
		}
					
		movePosition = true;
		invalidate(); //!!!!!
	}
	
	/**
	 * View contents of selected annotation.
	 * @param cursor database cursor
	 * @param event mouse event
	 * @param pagex page position x
	 * @param pagey page position y
	 * @param z zoom level
	 */
	private void updateActionMoveViewContents (Cursor cursor, MotionEvent event, float pagex, float pagey, float z) {
		float llx = 0, lly = 0, urx = 0, ury = 0, page = 0, preSize = 0;
		cursor = ((cx.pdf.android.pdfview.OpenFileActivity)activity).getAnnotsFromSQL(-1);
		if (cursor != null) {
			cannot = cursor;
		
			if  (cursor.moveToFirst()) {
		        do {
		        	if (cursor.getInt(cursor.getColumnIndex("flag")) != 3) {
			            page = getCurrentPageHeight(cursor.getInt(cursor.getColumnIndex("page"))-1);
			            preSize = pagePosition(cursor.getInt(cursor.getColumnIndex("page"))-1);
			            llx = cursor.getFloat(cursor.getColumnIndex("llx"));
			            lly = cursor.getFloat(cursor.getColumnIndex("lly"));
			            urx = cursor.getFloat(cursor.getColumnIndex("urx"));
			            ury = cursor.getFloat(cursor.getColumnIndex("ury"));
			            
			            if (touchPositionControl(event, llx, lly, urx, ury, z, page, preSize, pagex, pagey)) {
			            	this.annotTime = System.currentTimeMillis();
			            	cposition = cursor.getPosition();
			            }
		        	}
		        } while (cursor.moveToNext());
		    }
		}
	}
	
	/**
	 * Update new position of annotation.
	 * @param cursor database cursor
	 * @param event mouse event
	 * @param pagex page position x
	 * @param pagey page position y
	 * @param z zoom level
	 */
	private void updateActionMoveChangePosition (Cursor cursor, MotionEvent event, float pagex, float pagey, float z) {
		float llx = 0, lly = 0, urx = 0, ury = 0, page = 0, preSize = 0;
		cursor = ((cx.pdf.android.pdfview.OpenFileActivity)activity).getAnnotById(annotPosId > -1 ? annotPosId : annotResId);
			
		if (cursor != null) {
			if  (cursor.moveToFirst()) {
		       page = getCurrentPageHeight(cursor.getInt(cursor.getColumnIndex("page"))-1);
		       preSize = pagePosition(cursor.getInt(cursor.getColumnIndex("page"))-1);
		       llx = cursor.getFloat(cursor.getColumnIndex("llx"));
		       lly = cursor.getFloat(cursor.getColumnIndex("lly"));
		       urx = cursor.getFloat(cursor.getColumnIndex("urx"));
		       ury = cursor.getFloat(cursor.getColumnIndex("ury"));
			       
		       // update new position or size of annotation
		       if (!touchPositionControl(event, llx, lly, urx, ury, z, page, preSize, pagex, pagey)) {
		    	   // update new position (recalculate points)
		    	   if (annotResId > 0) ((cx.pdf.android.pdfview.OpenFileActivity)activity).saveNewAnnotPos(annotResId, 0, 0, 1);
		    	   // show text (changes successfully saved)
		    	   ((cx.pdf.android.pdfview.OpenFileActivity)activity).makeToast(R.string.save_changes);
			       writePosition = false;
			       annotMode = false;
			       annotPosId = -1;
			       annotResId = -1;
			       invalidate(); // !!!!!
		       }
			       
		   }
		}
	}
	
	/**
	 * Handle touch event coming from Android system.
	 */
	public boolean onTouch(View v, MotionEvent event) {
		Point pagePosition = this.getPagePositionOnScreen(0);
		float z = (this.scaling0 * (float)this.zoomLevel * 0.001f);
		Cursor cursor = null;
		float pagex = pagePosition.x;
		float pagey = pagePosition.y;
		
		/** Update new position or new size of annotation */
		if (event.getAction() == MotionEvent.ACTION_UP) {
			
			updateActionUp(event, pagex, pagey, z);
			
			realPageNumber = -1;
			
		/** Change position or size of annotation */
		} else if (event.getAction() == MotionEvent.ACTION_MOVE && annotMode) {
			if (realPageNumber < 0)
				while ((-event.getY() + pagey + pagePosition(realPageNumber)) < 0) {
					realPageNumber++;
				}

			float lly = (-event.getY() + pagey + pagePosition(realPageNumber));
			float llx = ( event.getX() - pagex);
			float tmp = 0;

			// y margin position control, exception for page number 1
			if (realPageNumber == 1) {
				if ((pagePosition(realPageNumber) - ((float) MARGIN_Y * zoomLevel * 0.001f))
					- (tmp+getCurrentPageHeight(0) - ((float) MARGIN_Y * zoomLevel * 0.001f)) >= lly) {
					Log.i(TAG, "Annotation cannot insert! Space position.");
				} else {
					updateActionMove(event.getX(), event.getY(), z);
				}
			} else {
				for (int i = 1; i < realPageNumber; i++) {
					if ((pagePosition(i) - ((float) MARGIN_Y * zoomLevel * 0.001f))
						- (tmp+getCurrentPageHeight(i-1) - ((float) MARGIN_Y * zoomLevel * 0.001f)) >= lly) {
						Log.i(TAG, "Annotation cannot insert! Space position.");
					} else {
						updateActionMove(event.getX(), event.getY(), z);
					}
					tmp += pagePosition(i);
				}
			}	
			
			//lastX = event.getX();
			//lastY = event.getY();
		}

		if (!gestureDetector.onTouchEvent(event))  {
			
			if (event.getAction() == MotionEvent.ACTION_DOWN) {
				downX = event.getX();
				downY = event.getY();
				lastX = downX;
				lastY = downY;
				
					
				// makes scrolling document
				if (!annotMode) {
					lockedVertically = verticalScrollLock;
					maxExcursionY = 0;
					scroller = null;
					// view contents of selected annotation
					updateActionMoveViewContents(cursor, event, pagex, pagey, z);

				// change position of selected annotation
				} else {
					updateActionMoveChangePosition(cursor, event, pagex, pagey, z);
				}
			
			} else if (event.getAction() == MotionEvent.ACTION_MOVE){
				if (lockedVertically && unlocksVerticalLock(event)) 
					lockedVertically = false;
				
				if (!annotMode) {
					float dx = event.getX() - lastX;
					float dy = event.getY() - lastY;
					
					float excursionY = Math.abs(event.getY() - downY);
	
					if (excursionY > maxExcursionY)
						maxExcursionY = excursionY;
					
					if (lockedVertically)
						dx = 0;
					
					doScroll((int)-dx, (int)-dy);
					
					lastX = event.getX();
					lastY = event.getY();
				} else {
					writePosition = true;
					invalidate(); //!!!!!
				}

				//lastX = event.getX();
				//lastY = event.getY();
				
			}
		}
		
		return true;
	}
	
	/**
	 * Evaluation of the annotation position.
	 * @param event mouse event 
	 * @param llx left low x position
	 * @param lly left low y position
	 * @param urx up right x position
	 * @param ury up right y position
	 * @param z zoom level
	 * @param page number of actual page 
	 * @param preSize
	 * @param pagex page position x
	 * @param pagey page position y
	 * @return true if mouse position belongs calculated position
	 */
	private boolean touchPositionControl (MotionEvent event, float llx, float lly, float urx, float ury, 
		float z, float page, float preSize, float pagex, float pagey) {
        float x1 = (llx * z  + pagex) < (urx * z  + pagex) ? (llx * z  + pagex) : (urx * z  + pagex);
        float x2 = (urx * z  + pagex) > (llx * z  + pagex) ? (urx * z  + pagex) : (llx * z  + pagex);

	    float y1 = ((page - (lly * z) + pagey + preSize) < (page - (ury * z) + pagey + preSize)) ? 
	        (page - (lly * z) + pagey + preSize) : (page - (ury * z) + pagey + preSize);
	    float y2 = ((page - (ury * z) + pagey + preSize) > (page - (lly * z) + pagey + preSize)) ? 
		    (page - (ury * z) + pagey + preSize) : (page - (lly * z) + pagey + preSize);
		     
        if (event.getX() >= x1 && event.getX() < x2 && event.getY() >= y1 && event.getY() < y2) {
        	return true;
        } else {
        	return false;
        }
	}
	
	/**
	 * Set "change position" mode or "change size" mode
	 * @param id
	 * @param action if 0 change position, if 1 change size
	 */
	public void setAnnotMoveSizeMode (int id, int action) {
		annotMode = true;
		annotResId = action > 0 ? id : -1;
		annotPosId = action > 0 ? -1 : id;
		invalidate(); //!!!!!
		return;
	}
	
	/***** END OF ANNOTATIONS TOUCH ACTIONS *****/
	
	/**
	 * Handle keyboard events
	 */
	public boolean onKey(View v, int keyCode, KeyEvent event) {
		if (this.pageWithVolume && event.getAction() == KeyEvent.ACTION_UP) {
			/* repeat is a little too fast sometimes, so trap these on up */
			switch(keyCode) {
				case KeyEvent.KEYCODE_VOLUME_UP:
					volumeUpIsDown = false;
					return true;
				case KeyEvent.KEYCODE_VOLUME_DOWN:
					volumeDownIsDown = false;
					return true;
			}
		}
		
		if (event.getAction() == KeyEvent.ACTION_DOWN) {
			int action = actions.getAction(keyCode);
			
			switch (keyCode) {
			case KeyEvent.KEYCODE_SEARCH:
				((cx.pdf.android.pdfview.OpenFileActivity)activity).showFindDialog();
				return true;
			case KeyEvent.KEYCODE_VOLUME_DOWN:
				if (action == Actions.ACTION_NONE)
					return false;
				if (!volumeDownIsDown) {
					/* Disable key repeat as on some devices the keys are a little too
					 * sticky for key repeat to work well.  TODO: Maybe key repeat disabling
					 * should be an option?  
					 */
					doAction(action);
				}
				volumeDownIsDown = true;
				return true;
			case KeyEvent.KEYCODE_VOLUME_UP:
				if (action == Actions.ACTION_NONE)
					return false;
				if (!this.pageWithVolume)
					return false;
				if (!volumeUpIsDown) {
					doAction(action);
				}
				volumeUpIsDown = true;
				return true;
			case KeyEvent.KEYCODE_DPAD_UP:
			case KeyEvent.KEYCODE_DPAD_DOWN:
			case KeyEvent.KEYCODE_DPAD_LEFT:
			case KeyEvent.KEYCODE_DPAD_RIGHT:
			case 92:
			case 93:
			case 94:
			case 95:
				doAction(action);
				return true;
				
			case KeyEvent.KEYCODE_DEL:
			case KeyEvent.KEYCODE_K:
				doAction(Actions.ACTION_SCREEN_UP);
				return true;
			case KeyEvent.KEYCODE_SPACE:
			case KeyEvent.KEYCODE_J:
				doAction(Actions.ACTION_SCREEN_DOWN);
				return true;
			case KeyEvent.KEYCODE_H:
				this.left -= this.getWidth() / 4;
				this.invalidate();
				return true;
			case KeyEvent.KEYCODE_L:
				this.left += this.getWidth() / 4;
				this.invalidate();
				return true;
			case KeyEvent.KEYCODE_O:
				zoom(1f/1.1f);
				return true;
			case KeyEvent.KEYCODE_P:
				zoom(1.1f);
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Test if specified rectangles intersect with each other.
	 * Uses Androids standard Rect class.
	 */
	private static boolean rectsintersect(
			int r1x0, int r1y0, int r1x1, int r1y1,
			int r2x0, int r2y0, int r2x1, int r2y1) {
		r1.set(r1x0, r1y0, r1x1, r1y1);
		return r1.intersects(r2x0, r2y0, r2x1, r2y1);
	}
	
	/**
	 * Used as a callback from pdf rendering code.
	 * TODO: only invalidate what needs to be painted, not the whole view
	 */
	public void onImagesRendered(Map<Tile,Bitmap> renderedTiles) {
		Rect rect = new Rect(); /* TODO: move out of onImagesRendered */

		int viewx0 = left - width/2;
		int viewy0 = top - height/2;
		
		int pageCount = this.pageSizes.length;
		float currentMarginX = this.getCurrentMarginX();
		float currentMarginY = this.getCurrentMarginY();
		
		viewx0 = adjustPosition(viewx0, width, (int)currentMarginX, 
				getCurrentMaxPageWidth());
		viewy0 = adjustPosition(viewy0, height, (int)currentMarginY,
				(int)getCurrentDocumentHeight());
		
		float currpageoff = currentMarginY;
		float renderAhead = this.pagesProvider.getRenderAhead();

		float pagex0;
		float pagex1;
		float pagey0 = 0;
		float pagey1;
		float x;
		float y;
		int pageWidth;
		int pageHeight;
		
		for(int i = 0; i < pageCount; ++i) {
			// is page i visible?

			pageWidth = this.getCurrentPageWidth(i);
			pageHeight = (int) this.getCurrentPageHeight(i);
			
			pagex0 = currentMarginX;
			pagex1 = (int)(currentMarginX + pageWidth);
			pagey0 = currpageoff;
			pagey1 = (int)(currpageoff + pageHeight);
			
			if (rectsintersect(
						(int)pagex0, (int)pagey0, (int)pagex1, (int)pagey1, // page rect in doc
						viewx0, viewy0, viewx0 + this.width, 
						viewy0 + this.height  
					))
			{
				x = pagex0 - viewx0;
				y = pagey0 - viewy0;
				
				for (Tile tile: renderedTiles.keySet()) {
					if (tile.getPage() == i) {
						Bitmap b = renderedTiles.get(tile); 
						
						rect.left = (int)(x + tile.getX());
						rect.top = (int)(y + tile.getY());
						rect.right = rect.left + b.getWidth();
						rect.bottom = rect.top + b.getHeight();	
					
						if (rect.intersects(0, 0, this.width, (int)(renderAhead*this.height))) {
							Log.v(TAG, "New bitmap forces redraw");
							postInvalidate();
							return;
						}
					}
				}
				
			}
			currpageoff += currentMarginY + this.getCurrentPageHeight(i);
		}
		Log.v(TAG, "New bitmap does not require redraw");
	}
	
	/**
	 * Handle rendering exception.
	 * Show error message and then quit parent activity.
	 * TODO: find a proper way to finish an activity when something bad happens in view.
	 */
	public void onRenderingException(RenderingException reason) {
		final Activity activity = this.activity;
		final String message = reason.getMessage();
		this.post(new Runnable() {
			public void run() {
    			AlertDialog errorMessageDialog = new AlertDialog.Builder(activity)
				.setTitle("Error")
				.setMessage(message)
				.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						activity.finish();
					}
				})
				.setOnCancelListener(new DialogInterface.OnCancelListener() {
					public void onCancel(DialogInterface dialog) {
						activity.finish();
					}
				})
				.create();
    			errorMessageDialog.show();
			}
		});
	}
	
	synchronized public void scrollToPage(int page) {
		scrollToPage(page, true);
	}
	
	public float pagePosition(int page) {
		float top = 0;
		
		for(int i = 0; i < page; ++i) {
			top += this.getCurrentPageHeight(i);
		}
		
		if (page > 0)
			top += (float)MARGIN_Y * this.zoomLevel * 0.001f * (float)(page);
		
		return top;		
	}

	/**
	 * Move current viewport over n-th page.
	 * Page is 0-based.
	 * @param page 0-based page number
	 */
	synchronized public void scrollToPage(int page, boolean positionAtTop) {
		float top;
		
		if (positionAtTop) {
			top = this.height/2 + pagePosition(page);
		}
		else {
			top = this.top - pagePosition(currentPage) + pagePosition(page);
		}

		this.top = (int)top;
		this.currentPage = page;
		this.invalidate();
	}
	
//	/**
//	 * Compute what's currently visible.
//	 * @return collection of tiles that define what's currently visible
//	 */
//	private Collection<Tile> computeVisibleTiles() {
//		LinkedList<Tile> tiles = new LinkedList<Tile>();
//		float viewx = this.left + (this.dragx1 - this.dragx);
//		float viewy = this.top + (this.dragy1 - this.dragy);
//		float pagex = MARGIN;
//		float pagey = MARGIN;
//		float pageWidth;
//		float pageHeight;
//		int tileix;
//		int tileiy;
//		int thisPageTileCountX;
//		int thisPageTileCountY;
//		float tilex;
//		float tiley;
//		for(int page = 0; page < this.pageSizes.length; ++page) {
//			
//			pageWidth = this.getCurrentPageWidth(page);
//			pageHeight = this.getCurrentPageHeight(page);
//			
//			thisPageTileCountX = (int)Math.ceil(pageWidth / TILE_SIZE);
//			thisPageTileCountY = (int)Math.ceil(pageHeight / TILE_SIZE);
//			
//			if (viewy + this.height < pagey) continue; /* before first visible page */
//			if (viewx > pagey + pageHeight) break; /* after last page */
//
//			for(tileix = 0; tileix < thisPageTileCountX; ++tileix) {
//				for(tileiy = 0; tileiy < thisPageTileCountY; ++tileiy) {
//					tilex = pagex + tileix * TILE_SIZE;
//					tiley = pagey + tileiy * TILE_SIZE;
//					if (rectsintersect(viewx, viewy, viewx+this.width, viewy+this.height,
//							tilex, tiley, tilex+TILE_SIZE, tiley+TILE_SIZE)) {
//						tiles.add(new Tile(page, this.zoomLevel, (int)tilex, (int)tiley, this.rotation));
//					}
//				}
//			}
//			
//			/* move to next page */
//			pagey += this.getCurrentPageHeight(page) + MARGIN;
//		}
//		return tiles;
//	}
//	synchronized Collection<Tile> getVisibleTiles() {
//		return this.visibleTiles;
//	}
	
	/**
	 * Rotate pages.
	 * Updates rotation variable, then invalidates view.
	 * @param rotation rotation
	 */
	synchronized public void rotate(int rotation) {
		this.rotation = (this.rotation + rotation) % 4;
		this.invalidate();
	}
	
	/**
	 * Set find mode.
	 * @param m true if pages view should display find results and find controls
	 */
	synchronized public void setFindMode(boolean m) {
		if (this.findMode != m) {
			this.findMode = m;
			if (!m) {
				this.findResults = null;
			}
		}
	}
	
	/**
	 * Return find mode.
	 * @return find mode - true if view is currently in find mode
	 */
	public boolean getFindMode() {
		return this.findMode;
	}

//	/**
//	 * Ask pages provider to focus on next find result.
//	 * @param forward direction of search - true for forward, false for backward
//	 */
//	public void findNext(boolean forward) {
//		this.pagesProvider.findNext(forward);
//		this.scrollToFindResult();
//		this.invalidate();
//	}
	
	/**
	 * Move viewport position to find result (if any).
	 * Does not call invalidate().
	 */
	public void scrollToFindResult(int n) {
		if (this.findResults == null || this.findResults.isEmpty()) return;
		Rect center = new Rect();
		FindResult findResult = this.findResults.get(n);
		for(Rect marker: findResult.markers) {
			center.union(marker);
		}
		int page = findResult.page;
		int x = 0;
		int y = 0;
		for(int p = 0; p < page; ++p) {
			Log.d(TAG, "adding page " + p + " to y: " + this.pageSizes[p][1]);
			y += this.pageSizes[p][1];
		}
		x += (center.left + center.right) / 2;
		y += (center.top + center.bottom) / 2;
		
		float marginX = this.getCurrentMarginX();
		float marginY = this.getCurrentMarginX();
	
		this.left = (int)(x * scaling0 * this.zoomLevel * 0.001f + marginX);
		this.top = (int)(y * scaling0 * this.zoomLevel * 0.001f + (page+1)*marginY);
	}
	
	/**
	 * Get the current page number
	 * 
	 * @return the current page. 0-based
	 */
	public int getCurrentPage() {
		return currentPage;
	}
	
	/**
	 * Get the current zoom level
	 * 
	 * @return the current zoom level
	 */
	public int getCurrentAbsoluteZoom() {
		return zoomLevel;
	}
	
	/**
	 * Get the current rotation
	 * 
	 * @return the current rotation
	 */
	public int getRotation() {
		return rotation;
	}
	
	/**
	 * Get page count.
	 */
	public int getPageCount() {
		return this.pageSizes.length;
	}
	
	/**
	 * Set find results.
	 */
	public void setFindResults(List<FindResult> results) {
		this.findResults = results;
	}
	
	/**
	 * Get current find results.
	 */
	public List<FindResult> getFindResults() {
		return this.findResults;
	}
	
	private void doFling(float vx, float vy) {
		float avx = vx > 0 ? vx : -vx;
		float avy = vy > 0 ? vy : -vy;
		
		if (avx < .25 * avy) {
			vx = 0;
		}
		else if (avy < .25 * avx) {
			vy = 0;
		}
		
		int marginX = (int)getCurrentMarginX();
		int marginY = (int)getCurrentMarginY();
		int minx = this.width/2 + getLowerBound(this.width, marginX, 
				getCurrentMaxPageWidth());
		int maxx = this.width/2 + getUpperBound(this.width, marginX, 
				getCurrentMaxPageWidth());
		int miny = this.height/2 + getLowerBound(this.width, marginY,
				  getCurrentDocumentHeight());
		int maxy = this.height/2 + getUpperBound(this.width, marginY,
				  getCurrentDocumentHeight());

		this.scroller = new Scroller(activity);
		this.scroller.fling(this.left, this.top, 
				(int)-vx, (int)-vy,
				minx, maxx,
				miny, maxy);
		invalidate();
	}
	
	private void doScroll(int dx, int dy) {
		this.left += dx;
		this.top += dy;
		invalidate();
	}
	
	/**
	 * Zoom down one level
	 */
	public void zoom(float value) {
		this.zoomLevel *= value;
		this.zoomLevel = (this.zoomLevel > 0) ? this.zoomLevel : 1; // fix zoom bug
		this.left *= value;
		this.top *= value;
		Log.d(TAG, "zoom level changed to " + this.zoomLevel);
		zoomToRestore = 0;
		this.invalidate();		
	}

	/* zoom to width */
	public void zoomWidth() {
		int page = currentPage < 0 ? 0 : currentPage;
		int pageWidth = getCurrentPageWidth(page);
		this.top = (this.top - this.height / 2) * this.width / pageWidth + this.height / 2;
		this.zoomLevel = this.zoomLevel * (this.width - 2*MARGIN_X) / pageWidth;
		this.left = (int) (this.width/2);
		zoomToRestore = 0;
		this.invalidate();		
	}

	/* zoom to fit */
	public void zoomFit() {
		int page = currentPage < 0 ? 0 : currentPage;
		int z1 = this.zoomLevel * this.width / getCurrentPageWidth(page);
		int z2 = (int)(this.zoomLevel * this.height / getCurrentPageHeight(page));
		this.zoomLevel = z2 < z1 ? z2 : z1;
		Point pos = getPagePositionInDocumentWithZoom(page);
		this.left = this.width/2 + pos.x;
		this.top = this.height/2 + pos.y;
		zoomToRestore = 0;
		this.invalidate();		
	}

	/**
	 * Set zoom
	 */
	public void setZoomLevel(int zoomLevel) {
		if (this.zoomLevel == zoomLevel)
			return;
		this.zoomLevel = zoomLevel;
		Log.d(TAG, "zoom level changed to " + this.zoomLevel);
		zoomToRestore = 0;
		this.invalidate();
	}
	
	
	/**
	 * Set rotation
	 */
	public void setRotation(int rotation) {
		if (this.rotation == rotation)
			return;
		this.rotation = rotation;
		Log.d(TAG, "rotation changed to " + this.rotation);
		this.invalidate();
	}
	
	
	public void setVerticalScrollLock(boolean verticalScrollLock) {
		this.verticalScrollLock = verticalScrollLock;
	}
	
	
	public void setColorMode(int colorMode) {
		this.colorMode = colorMode;
		this.invalidate();
	}


	public void setZoomIncrement(float step) {
		this.step = step;
	}
	
	public void setPageWithVolume(boolean pageWithVolume) {
		this.pageWithVolume = pageWithVolume;
	}
	

	private void getGoodTileSizes(int[] sizes, int pageWidth, int pageHeight) {
		sizes[0] = getGoodTileSize(pageWidth, MIN_TILE_WIDTH, MAX_TILE_WIDTH);		
		sizes[1] = getGoodTileSize(pageHeight, MIN_TILE_HEIGHT, MAX_TILE_PIXELS / sizes[0]); 
	}
	
	private int getGoodTileSize(int pageSize, int minSize, int maxSize) {
		if (pageSize <= 2)
			return 2;
		if (pageSize <= maxSize)
			return pageSize;
		int numInPageSize = (pageSize + maxSize - 1) / maxSize;
		int proposedSize = (pageSize + numInPageSize - 1) / numInPageSize;
		if (proposedSize < minSize)
			return minSize;
		else
			return proposedSize;
	}
	
	/* Get the upper and lower bounds for the viewpoint.  The document itself is
	 * drawn from margin to margin+docDim.   
	 */
	private int getLowerBound(int screenDim, int margin, int docDim) {
		if (docDim <= screenDim) {
			/* all pages can and do fit */
			return margin + docDim - screenDim;
		}
		else {
			/* document is too wide/tall to fit */
			return 0; 
		}
	}
	
	private int getUpperBound(int screenDim, int margin, int docDim) {
		if (docDim <= screenDim) {
			/* all pages can and do fit */
			return margin;
		}
		else {
			/* document is too wide/tall to fit */
			return 2 * margin + docDim - screenDim;
		}
	}
	
	private int adjustPosition(int pos, int screenDim, int margin, int docDim) {
		int min = getLowerBound(screenDim, margin, docDim);
		int max = getUpperBound(screenDim, margin, docDim);
		
		if (pos < min)
			return min;
		else if (max < pos)
			return max;
		else
			return pos;
	}
	
	public BookmarkEntry toBookmarkEntry() {
		return new BookmarkEntry(this.pageSizes.length, 
				this.currentPage, scaling0*zoomLevel, rotation, 
				this.left - this.getCurrentPageWidth(this.currentPage)/2 - MARGIN_X);
	}
	
	public void setSideMargins(Boolean sideMargins) {
		if (sideMargins)
			this.MARGIN_X = MARGIN_Y;
		else
			this.MARGIN_X = 0;
	}
	
	public void setDoubleTap(int doubleTapAction) {
		this.doubleTapAction = doubleTapAction;
	}
	
	public boolean doAction(int action) {
		float zoomValue = Actions.getZoomValue(action);
		if (0f < zoomValue) {
			zoom(zoomValue);
			return true;
		}
		switch(action) {
		case Actions.ACTION_FULL_PAGE_DOWN:
			scrollToPage(currentPage + 1, false);
			return true;
		case Actions.ACTION_FULL_PAGE_UP:
			scrollToPage(currentPage - 1, false);
			return true;
		case Actions.ACTION_PREV_PAGE:
			scrollToPage(currentPage - 1);
			return true;
		case Actions.ACTION_NEXT_PAGE:
			scrollToPage(currentPage + 1);
			return true;
		case Actions.ACTION_SCREEN_DOWN:
			this.top += this.getHeight() - 16;
			this.invalidate();
			return true;
		case Actions.ACTION_SCREEN_UP:
			this.top -= this.getHeight() - 16;
			this.invalidate();
			return true;
		default:
			return false;
		}
	}
	
	public void setActions(Actions actions) {
		this.actions = actions;
	}
	
	public void setEink(boolean eink) {
		this.eink = eink;
	}

	public void setNook2(boolean nook2) {
		this.nook2 = nook2;
	}

	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		// TODO Auto-generated method stub
		
	}
}
