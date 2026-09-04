package com.pindou.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.pindou.app.bead.BeadBrand;
import com.pindou.app.bead.BeadColor;
import com.pindou.app.bead.BeadInventory;
import com.pindou.app.bead.BeadPalettes;
import com.pindou.app.bead.BeadPattern;
import com.pindou.app.bead.ColorMath;
import com.pindou.app.bead.MlSegmenter;
import com.pindou.app.bead.PatternEngine;
import com.pindou.app.bead.PatternPatch;
import com.pindou.app.bead.StyleTransfer;
import com.pindou.app.export.EffectRenderer;
import com.pindou.app.export.PatternSheetRenderer;
import com.pindou.app.export.PdfExporter;
import com.pindou.app.provider.AppFileProvider;
import com.pindou.app.util.PatternShare;
import com.pindou.app.util.Anim;
import com.pindou.app.util.BeadCalendar;
import com.pindou.app.util.GallerySaver;
import com.pindou.app.util.ImageLoader;
import com.pindou.app.util.Jsons;
import com.pindou.app.util.ProjectStore;
import com.pindou.app.util.Skin;
import com.pindou.app.util.WatermarkRemover;
import com.pindou.app.view.PatternView;

import android.app.AlertDialog;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EditorActivity extends Activity {

    public static final String EXTRA_PHOTO_URI = "photo_uri";
    /** 空白画布模式:不开照片,直接手绘图纸 */
    public static final String EXTRA_BLANK = "blank_canvas";

    /** 首页工具卡片带入的"载入照片后自动执行"动作 */
    public static final int PENDING_NONE = 0;
    public static final int PENDING_WATERMARK = 2;
    public static int pendingAction = PENDING_NONE;

    /** 文字生成时,由 MainActivity 放入渲染好的位图(进程内存传递) */
    public static Bitmap pendingSource;
    /** 项目存档传递:MainActivity「我的项目」打开时放入,消费后置 null */
    public static String pendingProjectJson;
    /** 模板库建议画幅(边长),0 表示用默认 */
    public static int pendingSuggestedSize;

    private static final int REQ_STORAGE = 100;
    private static final int REQ_IMPORT = 101;
    private static final int MIN_SIZE = 8;
    private static final int MAX_SIZE = 160;
    private static final int[] ABSTRACT_CHOICES = {4, 6, 8, 10, 12, 16};
    private static final int[] BRICK_SIZES = {2, 3, 4, 6};
    private static final String[] BRICK_LABELS = {"轻度", "中度", "强度", "超强"};
    // 导出菜单项
    private static final int EXP_SHEET = 1;
    private static final int EXP_EFFECT = 2;
    private static final int EXP_SHARE = 3;
    private static final int EXP_PDF = 4;
    private static final int EXP_FILE = 6;

    // 状态
    private Bitmap source;
    private BeadPattern pattern;
    private int cols = 58;
    private int rows = 58;
    private int tierIdx = 2;            // 默认 90 色
    private boolean dither = false;
    private int brightness = 0;
    private int contrast = 0;
    private int saturation = 0;
    private int style = PatternEngine.STYLE_REALISTIC;
    private int brickIdx = 1;           // 默认 中度 3×3
    private boolean abstractUsePalette = true;
    private int abstractColors = 8;
    private boolean abstractSnap = true;
    private boolean bgRemove = false;   // 去背景
    /** 去背景容差 0~100,映射到 Lab 距离阈值约 14~58 */
    private int bgTolerance = 45;
    /** 圆形拼板 */
    private boolean roundBoard = false;
    /** 空白画布模式:没有源照片,直接在格子上作画 */
    private boolean blankCanvas = false;
    private boolean editCell = false;   // 点格修改开关
    /** 手动修格覆盖:格下标 -> 色板下标(-1 橡皮) */
    private final Map<Integer, Integer> editMap = new HashMap<>();
    /** 引擎原始输出(未套手动修改) */
    private BeadPattern rawPattern;
    /** 原始照片备份(AI 转图前),用于还原 */
    private Bitmap originalSource;
    private volatile boolean aiRunning = false;
    /** 旧版存档的"弱/中/强"档位 -> 新容差值 */
    private static final int[] LEGACY_BG_TOL = {30, 55, 80};

    // 画笔模式
    private boolean paintMode = false;
    /** 拼豆模式开关的命名监听器,便于互斥时安全关闭 */
    private final Switch.OnCheckedChangeListener beadAssistListener =
            new Switch.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (isChecked && paintMode) {
                        setPaintMode(false, true);
                    }
                    setBeadAssist(isChecked);
                }
            };
    /** 当前画笔是否是橡皮 */
    private boolean eraseOn = false;
    /** 画笔颜色(全色板下标) */
    private int brushPalIdx = -1;
    /** 涂色落格先记进 editMap,按帧合并刷新一次 UI,连续滑动不卡顿 */
    private boolean paintFlushQueued = false;

    // 撤销/重做(只覆盖手动修改:修格/画笔/橡皮/清除)
    private static final int MAX_UNDO = 40;
    private final ArrayDeque<Map<Integer, Integer>> undoStack = new ArrayDeque<>();
    private final ArrayDeque<Map<Integer, Integer>> redoStack = new ArrayDeque<>();
    /** 画笔一笔进行中,等真正改动了格子才入栈(一笔 = 一条撤销记录) */
    private boolean strokeSnapPending = false;

    private int genSeq = 0;
    private int pendingExport = 0;
    private boolean suppressSpinner = false;
    private boolean suppressAbsSpinner = false;

    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler();
    private final Runnable regenTask = new Runnable() {
        @Override
        public void run() {
            regenerate();
        }
    };

    // 视图
    private PatternView patternView;
    private TextView tabEffect, tabPattern, tabList;
    private View previewFrame, listFrame, controlsScroll, loadingOverlay;
    private TextView tvBoardHint, tvW, tvH, tvSummary;
    private TextView tvBright, tvContrast, tvSat;
    private View chip29, chip58, chip87, chip116;
    private View chipStyleReal, chipStyleAbs;
    private View abstractPanel;
    private View preprocessCardWrap, manualEditCardWrap;
    private View chipBrickLight, chipBrickMid, chipBrickStrong, chipBrickSuper;
    private View colorRow, snapRow;
    private Switch swEditBg, swEditCell, swPaint;
    private Switch swBeadAssist;
    private View beadAssistPanel, assistSwatch, btnAssistNext;
    private TextView tvAssistColor, tvAssistProgress;
    private final View[] chipLimits = new View[5];
    /** 色数上限档位(对应 COLOR_LIMITS),0 = 不限 */
    private int maxColorsIdx = 0;
    /** 从分享文件导入的图纸:可编辑/导出,但不支持改参数重新生成 */
    private boolean imported = false;
    // 生成质量包:众数取色 / 杂色清理 / CIEDE2000 精准配色
    private boolean dominant = false;
    private int denoise = 0;
    private boolean preciseColor = false;
    private static final int[] COLOR_LIMITS = {0, 12, 18, 26, 40};
    // 拼豆模式(逐色辅助 + 完成度标记)
    private boolean beadAssist = false;
    /** 当前辅助的颜色(palette 下标),-1 = 未选 */
    private int assistFocus = -1;
    /** 已拼好的格子(y*cols + x) */
    private final Set<Integer> beadDone = new HashSet<>();
    /** "今日完成"归属日期(yyyy-MM-dd),跨天自动归零 */
    private String beadDoneDay = "";
    /** 当天打卡完成的颗数 */
    private int beadDoneToday = 0;
    /** 缺豆替代建议:用到的色板下标 -> 建议替代色下标(库存够且色最近) */
    private final Map<Integer, Integer> beadSubstitutes = new HashMap<>();
    private View bgStrengthRow;
    private SeekBar sbBgTol;
    private TextView tvBgTol;
    private View btnClearEdits, brushPanel;
    private View brushSwatch, btnPickBrush, btnBrushEraser, brushName;
    /** 板子形状 */
    private View chipShapeRect, chipShapeRound, customSizeRow;
    /** 撤销/重做 */
    private View btnUndo, btnRedo;
    /** 照片变换 */
    private View btnMirrorH, btnMirrorV, btnRotate90;
    private View btnAiRestore;
    private View btnRemoveWatermark;
    private View btnStyleGhibli;
    private View btnAssistLocate, btnAssistCalendar, btnBrushMirror;
    private View assistToolsRow;
    private boolean paintMirror = false;
    private boolean dragDirty = false;
    private AlertDialog calendarDialog;
    private TextView tvLoading;
    private Spinner paletteSpinner, abstractColorSpinner;
    private Switch swDither, swSymbols, swGrid, swSnap, swKmeans;
    private Switch swDominant, swPrecise;
    private SeekBar sbDenoise;
    private TextView tvDenoise;
    private ListView beadList;
    private BeadAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);
        Skin.apply(getWindow().getDecorView());

        bindViews();
        setupTabs();
        setupControls();
        setupList();
        setupAiControls();
        applyPressFeedback();

        // 去背景小模型(U2NetP)预加载;失败自动回退颜色统计算法
        MlSegmenter.init(getApplicationContext());
        PatternEngine.setMlProvider(new PatternEngine.MlProvider() {
            @Override
            public float[] findSubjectProbs(int[] rgb, int w, int h) {
                return MlSegmenter.findSubjectProbs(rgb, w, h);
            }
        });

        findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        findViewById(R.id.btnMenu).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showExportMenu(v);
            }
        });
        findViewById(R.id.btnChangePhoto).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        findViewById(R.id.btnReset).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetSettings();
            }
        });
        btnUndo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                undoEdit();
            }
        });
        btnRedo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                redoEdit();
            }
        });
        updateUndoRedoButtons();

        String uriStr = getIntent().getStringExtra(EXTRA_PHOTO_URI);
        boolean blank = getIntent().getBooleanExtra(EXTRA_BLANK, false);
        if (pendingProjectJson != null) {
            // 「我的项目」打开的存档
            String json = pendingProjectJson;
            pendingProjectJson = null;
            loadProject(json);
        } else if (blank) {
            startBlankCanvas();
        } else if (pendingSource != null) {
            // 文字生成/模板库的位图
            source = pendingSource;
            originalSource = source;
            pendingSource = null;
            imported = false;
            int sug = pendingSuggestedSize;
            pendingSuggestedSize = 0;
            if (sug >= MIN_SIZE && sug <= MAX_SIZE) {
                cols = rows = sug;
                syncSizeUi();
            }
            regenerate();
        } else if (uriStr == null) {
            Toast.makeText(this, "没有选择照片", Toast.LENGTH_SHORT).show();
            finish();
            return;
        } else {
            loadPhoto(uriStr);
        }
    }

    private void bindViews() {
        patternView = findViewById(R.id.patternView);
        tabEffect = findViewById(R.id.tabEffect);
        tabPattern = findViewById(R.id.tabPattern);
        tabList = findViewById(R.id.tabList);
        previewFrame = findViewById(R.id.previewFrame);
        listFrame = findViewById(R.id.listFrame);
        controlsScroll = findViewById(R.id.controlsScroll);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        tvBoardHint = findViewById(R.id.tvBoardHint);
        tvW = findViewById(R.id.tvW);
        tvH = findViewById(R.id.tvH);
        tvBright = findViewById(R.id.tvBright);
        tvContrast = findViewById(R.id.tvContrast);
        tvSat = findViewById(R.id.tvSat);
        chip29 = findViewById(R.id.chip29);
        chip58 = findViewById(R.id.chip58);
        chip87 = findViewById(R.id.chip87);
        chip116 = findViewById(R.id.chip116);
        chipStyleReal = findViewById(R.id.chipStyleReal);
        chipStyleAbs = findViewById(R.id.chipStyleAbs);
        abstractPanel = findViewById(R.id.abstractPanel);
        preprocessCardWrap = findViewById(R.id.preprocessCardWrap);
        manualEditCardWrap = findViewById(R.id.manualEditCardWrap);
        abstractColorSpinner = findViewById(R.id.abstractColorSpinner);
        swEditBg = findViewById(R.id.swEditBg);
        bgStrengthRow = findViewById(R.id.bgStrengthRow);
        sbBgTol = findViewById(R.id.sbBgTol);
        tvBgTol = findViewById(R.id.tvBgTol);
        swEditCell = findViewById(R.id.swEditCell);
        btnClearEdits = findViewById(R.id.btnClearEdits);
        swPaint = findViewById(R.id.swPaint);
        swBeadAssist = findViewById(R.id.swBeadAssist);
        beadAssistPanel = findViewById(R.id.beadAssistPanel);
        assistSwatch = findViewById(R.id.assistSwatch);
        tvAssistColor = findViewById(R.id.tvAssistColor);
        btnAssistNext = findViewById(R.id.btnAssistNext);
        tvAssistProgress = findViewById(R.id.tvAssistProgress);
        int[] limitIds = {R.id.chipLimit0, R.id.chipLimit1, R.id.chipLimit2,
                R.id.chipLimit3, R.id.chipLimit4};
        for (int i = 0; i < limitIds.length; i++) chipLimits[i] = findViewById(limitIds[i]);
        brushPanel = findViewById(R.id.brushPanel);
        brushSwatch = findViewById(R.id.brushSwatch);
        brushName = findViewById(R.id.tvBrushName);
        btnPickBrush = findViewById(R.id.btnPickBrush);
        btnBrushEraser = findViewById(R.id.btnBrushEraser);
        btnMirrorH = findViewById(R.id.btnMirrorH);
        btnMirrorV = findViewById(R.id.btnMirrorV);
        btnRotate90 = findViewById(R.id.btnRotate90);
        chipShapeRect = findViewById(R.id.chipShapeRect);
        chipShapeRound = findViewById(R.id.chipShapeRound);
        customSizeRow = findViewById(R.id.customSizeRow);
        btnUndo = findViewById(R.id.btnUndo);
        btnRedo = findViewById(R.id.btnRedo);
        btnAiRestore = findViewById(R.id.btnAiRestore);
        btnRemoveWatermark = findViewById(R.id.btnRemoveWatermark);
        btnStyleGhibli = findViewById(R.id.btnStyleGhibli);
        btnAssistLocate = findViewById(R.id.btnAssistLocate);
        btnAssistCalendar = findViewById(R.id.btnAssistCalendar);
        btnBrushMirror = findViewById(R.id.btnBrushMirror);
        assistToolsRow = findViewById(R.id.assistToolsRow);
        tvLoading = findViewById(R.id.tvLoading);
        chipBrickLight = findViewById(R.id.chipBrickLight);
        chipBrickMid = findViewById(R.id.chipBrickMid);
        chipBrickStrong = findViewById(R.id.chipBrickStrong);
        chipBrickSuper = findViewById(R.id.chipBrickSuper);
        colorRow = findViewById(R.id.colorRow);
        snapRow = findViewById(R.id.snapRow);
        swKmeans = findViewById(R.id.swKmeans);
        beadList = findViewById(R.id.beadList);
        swDither = findViewById(R.id.swDither);
        swDominant = findViewById(R.id.swDominant);
        swPrecise = findViewById(R.id.swPrecise);
        sbDenoise = findViewById(R.id.sbDenoise);
        tvDenoise = findViewById(R.id.tvDenoise);
        swSymbols = findViewById(R.id.swSymbols);
        swGrid = findViewById(R.id.swGrid);
        swSnap = findViewById(R.id.swSnap);
        paletteSpinner = findViewById(R.id.paletteSpinner);
    }

    private void loadPhoto(final String uriStr) {
        showLoading(true);
        exec.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final Bitmap bmp = ImageLoader.load(getContentResolver(),
                            Uri.parse(uriStr), 2048);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            source = bmp;
                            originalSource = bmp;
                            regenerate();
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showLoading(false);
                            Toast.makeText(EditorActivity.this,
                                    "这张照片读取失败,换一张试试吧", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    });
                }
            }
        });
    }

    // ---------------- 标签页 ----------------

    /** 返回主页:页面沉底过渡(系统返回键同样生效) */
    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.enter_undim, R.anim.exit_down);
    }

    /** 给所有"能按的"小控件挂按压缩放反馈(贴纸手感:按下去陷一下再弹回) */
    private void applyPressFeedback() {
        int[] ids = {
                R.id.tabEffect, R.id.tabPattern, R.id.tabList,
                R.id.chipStyleReal, R.id.chipStyleAbs,
                R.id.chipBrickLight, R.id.chipBrickMid, R.id.chipBrickStrong, R.id.chipBrickSuper,
                R.id.chipShapeRect, R.id.chipShapeRound,
                R.id.chipLimit0, R.id.chipLimit1, R.id.chipLimit2, R.id.chipLimit3, R.id.chipLimit4,
                R.id.btnAiRestore, R.id.btnRemoveWatermark,
                R.id.btnAssistNext, R.id.btnUndo, R.id.btnRedo,
                R.id.btnMirrorH, R.id.btnMirrorV, R.id.btnRotate90
        };
        for (int id : ids) {
            View v = findViewById(id);
            if (v != null) Anim.pressScale(v);
        }
    }

    private void setupTabs() {        tabEffect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectTab(0);
            }
        });
        tabPattern.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectTab(1);
            }
        });
        tabList.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectTab(2);
            }
        });
        selectTab(0);
    }

    private void selectTab(int which) {
        tabEffect.setSelected(which == 0);
        tabPattern.setSelected(which == 1);
        tabList.setSelected(which == 2);
        if (which == 2) {
            previewFrame.setVisibility(View.GONE);
            controlsScroll.setVisibility(View.GONE);
            listFrame.setVisibility(View.VISIBLE);
            Anim.pulse(listFrame);
        } else {
            previewFrame.setVisibility(View.VISIBLE);
            controlsScroll.setVisibility(View.VISIBLE);
            listFrame.setVisibility(View.GONE);
            Anim.pulse(previewFrame);
            patternView.setMode(which == 0 ? PatternView.MODE_EFFECT
                    : PatternView.MODE_PATTERN);
        }
    }

    // ---------------- 参数控件 ----------------

    private void setupControls() {
        // 尺寸预设(画幅变化会使手动修格失效)
        View.OnClickListener preset = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int id = v.getId();
                if (id == R.id.chip29) {
                    cols = rows = 29;
                } else if (id == R.id.chip58) {
                    cols = rows = 58;
                } else if (id == R.id.chip87) {
                    cols = rows = 87;
                } else {
                    cols = rows = 116;
                }
                structureChanged();
            }
        };
        chip29.setOnClickListener(preset);
        chip58.setOnClickListener(preset);
        chip87.setOnClickListener(preset);
        chip116.setOnClickListener(preset);

        // 板子形状:方形 / 圆形
        View.OnClickListener shapeClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setRound(v.getId() == R.id.chipShapeRound);
            }
        };
        chipShapeRect.setOnClickListener(shapeClick);
        chipShapeRound.setOnClickListener(shapeClick);

        // 宽高步进
        findViewById(R.id.btnWMinus).setOnClickListener(stepper(false, true));
        findViewById(R.id.btnWPlus).setOnClickListener(stepper(true, true));
        findViewById(R.id.btnHMinus).setOnClickListener(stepper(false, false));
        findViewById(R.id.btnHPlus).setOnClickListener(stepper(true, false));

        // 色板(通用 4 档 + 品牌官方色号表)
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, BeadPalettes.selNames());
        spinnerAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        paletteSpinner.setAdapter(spinnerAdapter);
        suppressSpinner = true;
        paletteSpinner.setSelection(tierIdx);
        suppressSpinner = false;
        paletteSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (suppressSpinner) return;
                tierIdx = position;
                editMap.clear();   // 色板体系变了,修格下标失效
                structureChanged();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // 风格:写实 / 抽象(卡通、动漫效果请用 AI 图像风格转图)
        View.OnClickListener styleClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setStyle(v.getId() == R.id.chipStyleAbs
                        ? PatternEngine.STYLE_ABSTRACT : PatternEngine.STYLE_REALISTIC);
            }
        };
        chipStyleReal.setOnClickListener(styleClick);
        chipStyleAbs.setOnClickListener(styleClick);
        setStyle(PatternEngine.STYLE_REALISTIC);

        // 一键去背景
        swEditBg.setOnCheckedChangeListener(new Switch.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                bgRemove = isChecked;
                if (isChecked) {
                    Anim.expand(bgStrengthRow);
                } else {
                    Anim.collapse(bgStrengthRow);
                }
                scheduleRegen();
            }
        });
        // 容差滑杆(120ms 防抖在 scheduleRegen 里,拖动实时出图)
        sbBgTol.setMax(100);
        sbBgTol.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                bgTolerance = progress;
                tvBgTol.setText(progress + "%");
                scheduleRegen();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        syncBgUi();

        // 手动修格(点格弹选色)与画笔模式互斥
        swEditCell.setOnCheckedChangeListener(new Switch.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                editCell = isChecked;
                if (isChecked && paintMode) {
                    setPaintMode(false, true);
                }
                if (isChecked && beadAssist) {
                    swBeadAssist.setOnCheckedChangeListener(null);
                    swBeadAssist.setChecked(false);
                    swBeadAssist.setOnCheckedChangeListener(beadAssistListener);
                    setBeadAssist(false);
                }
                Toast.makeText(EditorActivity.this,
                        isChecked ? "已开启:去「图纸」页点任意格子换色"
                                : "已关闭点格修改",
                        Toast.LENGTH_SHORT).show();
            }
        });
        btnClearEdits.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (editMap.isEmpty()) return;
                pushUndoState();
                editMap.clear();
                applyEditsToUi();
                updateEditsButton();
            }
        });
        patternView.setOnCellTapListener(new PatternView.OnCellTapListener() {
            @Override
            public void onCellTap(int cellX, int cellY) {
                if (pattern == null) return;
                if (pattern.outsideShape(cellX, cellY)) return;   // 圆形板板外无格
                if (beadAssist) {
                    int idx = pattern.cellAt(cellX, cellY);
                    if (idx < 0) return;
                    int key = cellY * pattern.cols + cellX;
                    boolean added = beadDone.add(key);
                    if (!added) beadDone.remove(key);
                    rollBeadDay();
                    beadDoneToday = Math.max(0, beadDoneToday + (added ? 1 : -1));
                    BeadCalendar.add(EditorActivity.this, added ? 1 : -1);
                    updateAssistUi();
                    updateSummary();
                    adapter.notifyDataSetChanged();
                    patternView.invalidate();
                    return;
                }
                if (!editCell) return;
                showColorPicker(cellY * pattern.cols + cellX);
            }
        });
        // 画笔模式下长按一格 = 油漆桶填充
        patternView.setOnCellLongPressListener(new PatternView.OnCellLongPressListener() {
            @Override
            public void onCellLongPress(int cellX, int cellY) {
                floodFill(cellX, cellY);
            }
        });
        // 拼豆模式按住滑动 = 连续标记完成
        patternView.setOnAssistDragListener(new PatternView.OnAssistDragListener() {
            @Override
            public void onAssistDragCell(int cellX, int cellY) {
                if (pattern == null) return;
                if (pattern.outsideShape(cellX, cellY)) return;
                int idx = pattern.cellAt(cellX, cellY);
                if (idx < 0) return;
                int key = cellY * pattern.cols + cellX;
                if (beadDone.add(key)) {
                    rollBeadDay();
                    beadDoneToday++;
                    BeadCalendar.add(EditorActivity.this, 1);
                    patternView.invalidate();
                    dragDirty = true;
                }
            }

            @Override
            public void onAssistDragEnd() {
                if (dragDirty) {
                    dragDirty = false;
                    updateAssistUi();
                    updateSummary();
                    adapter.notifyDataSetChanged();
                }
            }
        });

        // 画笔模式:在图纸上滑动连续涂色
        swPaint.setOnCheckedChangeListener(new Switch.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setPaintMode(isChecked, false);
            }
        });
        // 拼豆模式:逐色辅助 + 完成度标记(与画笔/修格互斥)
        swBeadAssist.setOnCheckedChangeListener(beadAssistListener);
        btnAssistNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cycleAssistColor();
            }
        });
        btnAssistNext.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                locateAssistUndone();
                return true;
            }
        });
        btnAssistLocate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                locateAssistUndone();
            }
        });
        btnAssistCalendar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCalendarDialog();
            }
        });
        btnBrushMirror.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                paintMirror = !paintMirror;
                btnBrushMirror.setSelected(paintMirror);
                Toast.makeText(EditorActivity.this,
                        paintMirror ? "镜像已开:左右同时落笔" : "镜像已关",
                        Toast.LENGTH_SHORT).show();
            }
        });
        // 色数上限(降色数)
        View.OnClickListener limitClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int idx = 0;
                for (int i = 0; i < chipLimits.length; i++) {
                    boolean sel = chipLimits[i] == v;
                    chipLimits[i].setSelected(sel);
                    if (sel) idx = i;
                }
                if (idx != maxColorsIdx) {
                    maxColorsIdx = idx;
                    scheduleRegen();
                }
            }
        };
        for (View cv : chipLimits) cv.setOnClickListener(limitClick);
        for (int i = 0; i < chipLimits.length; i++) {
            chipLimits[i].setSelected(i == maxColorsIdx);
        }
        btnPickBrush.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showBrushPicker();
            }
        });
        btnBrushEraser.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                eraseOn = !eraseOn;
                syncBrushUi();
            }
        });
        patternView.setOnPaintListener(new PatternView.OnPaintListener() {
            @Override
            public void onStrokeStart() {
                strokeSnapPending = true;
            }

            @Override
            public void onPaintCell(int cellX, int cellY) {
                paintAt(cellX, cellY);
            }
        });

        // 照片变换(镜像 / 旋转会同步映射手动修格记录)
        View.OnClickListener tr = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int id = v.getId();
                if (id == R.id.btnMirrorH) transformSource(Transform.MIRROR_H);
                else if (id == R.id.btnMirrorV) transformSource(Transform.MIRROR_V);
                else transformSource(Transform.ROTATE_90);
            }
        };
        btnMirrorH.setOnClickListener(tr);
        btnMirrorV.setOnClickListener(tr);
        btnRotate90.setOnClickListener(tr);

        // 抽象程度(砖块大小)
        View.OnClickListener brickClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int id = v.getId();
                if (id == R.id.chipBrickLight) brickIdx = 0;
                else if (id == R.id.chipBrickMid) brickIdx = 1;
                else if (id == R.id.chipBrickStrong) brickIdx = 2;
                else brickIdx = 3;
                syncBrickUi();
                scheduleRegen();
            }
        };
        chipBrickLight.setOnClickListener(brickClick);
        chipBrickMid.setOnClickListener(brickClick);
        chipBrickStrong.setOnClickListener(brickClick);
        chipBrickSuper.setOnClickListener(brickClick);
        syncBrickUi();

        // 主色调限定开关:控制颜色数量/吸附两行的显隐
        swKmeans.setOnCheckedChangeListener(new Switch.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                abstractUsePalette = isChecked;
                int vis = isChecked ? View.VISIBLE : View.GONE;
                colorRow.setVisibility(vis);
                snapRow.setVisibility(vis);
                scheduleRegen();
            }
        });

        // 抽象画:主色数量
        String[] absLabels = new String[ABSTRACT_CHOICES.length];
        for (int i = 0; i < ABSTRACT_CHOICES.length; i++) {
            absLabels[i] = ABSTRACT_CHOICES[i] + " 色";
        }
        ArrayAdapter<String> absAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, absLabels);
        absAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        abstractColorSpinner.setAdapter(absAdapter);
        int absDefault = 2; // 8 色
        suppressAbsSpinner = true;
        abstractColorSpinner.setSelection(absDefault);
        suppressAbsSpinner = false;
        abstractColorSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (suppressAbsSpinner) return;
                abstractColors = ABSTRACT_CHOICES[position];
                scheduleRegen();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // 抽象画:吸附拼豆色板
        swSnap.setOnCheckedChangeListener(new Switch.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                abstractSnap = isChecked;
                scheduleRegen();
            }
        });

        // 开关
        swDither.setOnCheckedChangeListener(new Switch.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                dither = isChecked;
                scheduleRegen();
            }
        });
        swDominant.setOnCheckedChangeListener(new Switch.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                dominant = isChecked;
                scheduleRegen();
            }
        });
        swPrecise.setOnCheckedChangeListener(new Switch.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                preciseColor = isChecked;
                scheduleRegen();
            }
        });
        String[] denoiseLabels = {"关", "轻", "中", "强"};
        sbDenoise.setMax(3);
        sbDenoise.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                denoise = progress;
                tvDenoise.setText(denoiseLabels[progress]);
                scheduleRegen();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        swSymbols.setOnCheckedChangeListener(new Switch.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                patternView.setShowSymbols(isChecked);
            }
        });
        swGrid.setOnCheckedChangeListener(new Switch.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                patternView.setShowGrid(isChecked);
            }
        });

        // 滑杆
        bindSeek(R.id.sbBright, tvBright);
        bindSeek(R.id.sbContrast, tvContrast);
        bindSeek(R.id.sbSat, tvSat);

        syncSizeUi();
    }

    private void setStyle(int s) {
        boolean changed = style != s;
        style = s;
        if (changed) editMap.clear();
        chipStyleReal.setSelected(s == PatternEngine.STYLE_REALISTIC);
        chipStyleAbs.setSelected(s == PatternEngine.STYLE_ABSTRACT);
        if (s == PatternEngine.STYLE_ABSTRACT) {
            Anim.expand(abstractPanel);
        } else {
            abstractPanel.setVisibility(View.GONE);
        }
        if (changed && blankCanvas) {
            style = PatternEngine.STYLE_REALISTIC;   // 空白画布不参与风格
            chipStyleReal.setSelected(true);
            chipStyleAbs.setSelected(false);
            abstractPanel.setVisibility(View.GONE);
            return;
        }
        if (changed) scheduleRegen();
    }

    private void syncBrickUi() {
        chipBrickLight.setSelected(brickIdx == 0);
        chipBrickMid.setSelected(brickIdx == 1);
        chipBrickStrong.setSelected(brickIdx == 2);
        chipBrickSuper.setSelected(brickIdx == 3);
    }

    private View.OnClickListener stepper(final boolean plus, final boolean width) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (width) {
                    cols = clampSize(cols + (plus ? 1 : -1));
                } else {
                    rows = clampSize(rows + (plus ? 1 : -1));
                }
                structureChanged();
            }
        };
    }

    /** 尺寸/色板等结构性变化后调用:清修格,空白画布直接重建,照片模式重新生成 */
    private void structureChanged() {
        syncSizeUi();
        invalidateEdits();
        if (blankCanvas) {
            rebuildBlankRaw();
        } else {
            scheduleRegen();
        }
    }

    /** 手动修格基于格下标,尺寸/形状/风格/色板一变就整体失效 */
    private void invalidateEdits() {
        if (!editMap.isEmpty()) {
            editMap.clear();
        }
        undoStack.clear();
        redoStack.clear();
        updateUndoRedoButtons();
    }

    // ---------------- 撤销 / 重做 ----------------

    /** 进入一条新的可撤销操作前调用:保存当前手动修改快照 */
    private void pushUndoState() {
        undoStack.addLast(new HashMap<>(editMap));
        if (undoStack.size() > MAX_UNDO) undoStack.removeFirst();
        redoStack.clear();
        updateUndoRedoButtons();
    }

    private void undoEdit() {
        if (undoStack.isEmpty()) {
            Toast.makeText(this, "没有可撤销的操作了", Toast.LENGTH_SHORT).show();
            return;
        }
        redoStack.addLast(new HashMap<>(editMap));
        editMap.clear();
        editMap.putAll(undoStack.removeLast());
        applyEditsToUi();
        updateEditsButton();
        updateUndoRedoButtons();
    }

    private void redoEdit() {
        if (redoStack.isEmpty()) {
            Toast.makeText(this, "没有可重做的操作了", Toast.LENGTH_SHORT).show();
            return;
        }
        undoStack.addLast(new HashMap<>(editMap));
        editMap.clear();
        editMap.putAll(redoStack.removeLast());
        applyEditsToUi();
        updateEditsButton();
        updateUndoRedoButtons();
    }

    private void updateUndoRedoButtons() {
        if (btnUndo == null) return;
        btnUndo.setAlpha(undoStack.isEmpty() ? 0.35f : 1f);
        btnRedo.setAlpha(redoStack.isEmpty() ? 0.35f : 1f);
    }

    // ---------------- 板子形状 ----------------

    private void setRound(boolean round) {
        if (roundBoard == round) return;
        roundBoard = round;
        if (round && rows != cols) {
            cols = rows = Math.min(cols, rows);
        }
        chipShapeRect.setSelected(!round);
        chipShapeRound.setSelected(round);
        customSizeRow.setVisibility(round ? View.GONE : View.VISIBLE);
        invalidateEdits();
        structureChanged();
    }

    private int clampSize(int v) {
        return Math.max(MIN_SIZE, Math.min(MAX_SIZE, v));
    }

    private void syncSizeUi() {
        tvW.setText(String.format(Locale.CHINA, "%d", cols));
        tvH.setText(String.format(Locale.CHINA, "%d", rows));
        chip29.setSelected(cols == 29 && rows == 29);
        chip58.setSelected(cols == 58 && rows == 58);
        chip87.setSelected(cols == 87 && rows == 87);
        chip116.setSelected(cols == 116 && rows == 116);
        if (chipShapeRect != null) {
            chipShapeRect.setSelected(!roundBoard);
            chipShapeRound.setSelected(roundBoard);
        }
        if (roundBoard) {
            tvBoardHint.setText(String.format(Locale.CHINA,
                    "圆形板直径 %d 格 · 成品约 %.0f cm(标准 5mm 豆)",
                    cols, cols * 0.5));
        } else {
            int boards = (int) (Math.ceil(cols / 29.0) * Math.ceil(rows / 29.0));
            tvBoardHint.setText(String.format(Locale.CHINA,
                    "需要 29×29 拼板 %d 块 · 成品约 %.0f × %.0f cm(标准 5mm 豆)",
                    boards, cols * 0.5, rows * 0.5));
        }
    }

    private void bindSeek(int seekId, final TextView valueLabel) {
        SeekBar sb = findViewById(seekId);
        final int[] value = new int[1];
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                value[0] = progress - 100;
                String sign = value[0] > 0 ? "+" : "";
                valueLabel.setText(String.format(Locale.CHINA, "%s%d", sign, value[0]));
                if (fromUser) {
                    int id = seekBar.getId();
                    if (id == R.id.sbBright) brightness = value[0];
                    else if (id == R.id.sbContrast) contrast = value[0];
                    else saturation = value[0];
                    scheduleRegen();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    private void resetSettings() {
        cols = rows = 58;
        tierIdx = 2;
        dither = false;
        imported = false;
        dominant = false;
        denoise = 0;
        preciseColor = false;
        brightness = contrast = saturation = 0;
        brickIdx = 1;
        syncBrickUi();
        swKmeans.setChecked(true);   // 回调里会同步 abstractUsePalette 与行显隐
        abstractColors = 8;
        abstractSnap = true;
        swEditBg.setChecked(false);
        bgTolerance = 45;
        syncBgUi();
        editCell = false;
        swEditCell.setChecked(false);
        setPaintMode(false, true);
        editMap.clear();
        updateEditsButton();
        if (blankCanvas) {
            cols = rows = 29;   // 空白画布恢复到单板尺寸
        }
        roundBoard = false;
        chipShapeRect.setSelected(true);
        chipShapeRound.setSelected(false);
        customSizeRow.setVisibility(View.VISIBLE);
        syncSizeUi();
        suppressSpinner = true;
        paletteSpinner.setSelection(tierIdx);
        suppressSpinner = false;
        int absPos = 2;
        for (int i = 0; i < ABSTRACT_CHOICES.length; i++) {
            if (ABSTRACT_CHOICES[i] == 8) absPos = i;
        }
        suppressAbsSpinner = true;
        abstractColorSpinner.setSelection(absPos);
        suppressAbsSpinner = false;
        swSnap.setChecked(true);
        swDither.setChecked(false);
        swDominant.setChecked(false);
        swPrecise.setChecked(false);
        sbDenoise.setProgress(0);
        tvDenoise.setText("关");
        swSymbols.setChecked(true);
        swGrid.setChecked(true);
        style = PatternEngine.STYLE_REALISTIC;
        chipStyleReal.setSelected(true);
        chipStyleAbs.setSelected(false);
        abstractPanel.setVisibility(View.GONE);
        setSeek(R.id.sbBright, 100);
        setSeek(R.id.sbContrast, 100);
        setSeek(R.id.sbSat, 100);
        structureChanged();
    }

    private void setSeek(int id, int progress) {
        // 程序设置进度会触发 onProgressChanged(fromUser=false),只更新标签,不会重新生成
        ((SeekBar) findViewById(id)).setProgress(progress);
    }

    /** 去水印与照片还原入口(原 AI 卡片,现并入照片预处理区) */
    private void setupAiControls() {
        btnAiRestore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                restoreOriginal();
            }
        });
        btnRemoveWatermark.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showWatermarkDialog();
            }
        });
        btnStyleGhibli.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showStyleDialog();
            }
        });
    }

    // ---------------- AI 风格化(AnimeGANv3,离线) ----------------

    private void showStyleDialog() {
        if (aiRunning) return;
        if (source == null) {
            Toast.makeText(this, "空白画布不需要风格化,先导入照片", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("🎨 吉卜力风(AI 风格化)")
                .setMessage("用内置的 AnimeGANv3 模型把照片变成吉卜力动画风,"
                        + "颜色块面更干净,转出来的拼豆图纸轮廓更好看。\n\n"
                        + "纯本地离线推理,约 2~10 秒;\n"
                        + "结果会替换当前照片,之后点「↺ 恢复原始照片」可随时还原。")
                .setPositiveButton("开始", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        runStyle();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void runStyle() {
        aiRunning = true;
        showLoading(true, "AI 风格化中…");
        final Bitmap bmp = source;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!StyleTransfer.ensureInit(EditorActivity.this)) {
                        throw new Exception("风格化模型不可用");
                    }
                    int[] px = new int[bmp.getWidth() * bmp.getHeight()];
                    bmp.getPixels(px, 0, bmp.getWidth(), 0, 0,
                            bmp.getWidth(), bmp.getHeight());
                    Object[] r = StyleTransfer.stylize(px,
                            bmp.getWidth(), bmp.getHeight());
                    if (r == null) throw new Exception("推理失败,请重试");
                    final int[] outPx = (int[]) r[0];
                    final int ow = (Integer) r[1];
                    final int oh = (Integer) r[2];
                    final Bitmap out = Bitmap.createBitmap(ow, oh,
                            Bitmap.Config.ARGB_8888);
                    out.setPixels(outPx, 0, ow, 0, 0, ow, oh);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            aiRunning = false;
                            showLoading(false);
                            if (source != null && source != originalSource) {
                                source.recycle();
                            }
                            source = out;
                            btnAiRestore.setVisibility(View.VISIBLE);
                            Anim.expand(btnAiRestore);
                            imported = false;
                            regenerate();
                            Toast.makeText(EditorActivity.this,
                                    "风格化完成,照片已替换", Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (final Throwable t) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            aiRunning = false;
                            showLoading(false);
                            Toast.makeText(EditorActivity.this,
                                    "风格化失败:" + t.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    // ---------------- 去水印 ----------------

    /**
     * 框选水印修复:用户在照片上拖一个框盖住水印,
     * 框内像素用周边真实像素扩散填充(WatermarkRemover)。
     * 适合清理外部 AI 工具出图自带的水印/角标,再转拼豆图纸。
     */
    private void showWatermarkDialog() {
        if (aiRunning) return;
        if (source == null) {
            Toast.makeText(this, "还没有照片", Toast.LENGTH_SHORT).show();
            return;
        }
        final Bitmap bmp = source;
        float dm = getResources().getDisplayMetrics().density;
        int maxW = Math.round(320 * dm);
        int maxH = Math.round(380 * dm);
        float scale = Math.min(maxW / (float) bmp.getWidth(),
                maxH / (float) bmp.getHeight());
        final int dw = Math.max(1, Math.round(bmp.getWidth() * scale));
        final int dh = Math.max(1, Math.round(bmp.getHeight() * scale));

        ImageView iv = new ImageView(this);
        iv.setImageBitmap(bmp);
        iv.setScaleType(ImageView.ScaleType.FIT_XY);
        final WatermarkRectView overlay = new WatermarkRectView(this);

        FrameLayout wrap = new FrameLayout(this);
        wrap.addView(iv, new FrameLayout.LayoutParams(dw, dh));
        wrap.addView(overlay, new FrameLayout.LayoutParams(dw, dh));
        int pad = Math.round(12 * dm);
        wrap.setPadding(pad, 0, pad, 0);
        Skin.apply(wrap);

        new AlertDialog.Builder(this)
                .setTitle("🩹 去水印")
                .setMessage("用来清除别的 APP 出图自带的水印/角标。\n"
                        + "用法:在图上拖一个框,把水印完整盖住、框紧贴水印,点「修复」;\n"
                        + "不要圈进大块背景。图上有多个水印时,修完一次再框下一处即可。")
                .setView(wrap)
                .setPositiveButton("修复", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        Rect r = overlay.rect;
                        if (r == null || r.width() < 8 || r.height() < 8) {
                            Toast.makeText(EditorActivity.this,
                                    "先在图上拖一个框盖住水印", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        applyWatermarkFix(bmp, r, dw, dh);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 后台执行修复,完成后替换 source 并重新生成图纸(走 AI 转图同款替换流程) */
    private void applyWatermarkFix(final Bitmap bmp, final Rect sel, final int dw, final int dh) {
        // 视口坐标 -> 像素坐标,外扩 2px 盖住水印抗锯齿边
        final int bx0 = Math.max(0, Math.round(sel.left * bmp.getWidth() / (float) dw) - 2);
        final int by0 = Math.max(0, Math.round(sel.top * bmp.getHeight() / (float) dh) - 2);
        final int bx1 = Math.min(bmp.getWidth() - 1,
                Math.round(sel.right * bmp.getWidth() / (float) dw) + 2);
        final int by1 = Math.min(bmp.getHeight() - 1,
                Math.round(sel.bottom * bmp.getHeight() / (float) dh) + 2);
        aiRunning = true;
        showLoading(true, "正在修复水印…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final Bitmap out = bmp.copy(Bitmap.Config.ARGB_8888, true);
                    int[] px = new int[out.getWidth() * out.getHeight()];
                    out.getPixels(px, 0, out.getWidth(), 0, 0, out.getWidth(), out.getHeight());
                    WatermarkRemover.remove(px, out.getWidth(), out.getHeight(),
                            bx0, by0, bx1, by1);
                    out.setPixels(px, 0, out.getWidth(), 0, 0, out.getWidth(), out.getHeight());
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            aiRunning = false;
                            showLoading(false);
                            if (source != null && source != originalSource) {
                                source.recycle();
                            }
                            source = out;
                            btnAiRestore.setVisibility(View.VISIBLE);
                            Anim.expand(btnAiRestore);
                            regenerate();
                            Toast.makeText(EditorActivity.this,
                                    "已修复,还有水印可再框一次", Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (final Throwable t) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            aiRunning = false;
                            showLoading(false);
                            Toast.makeText(EditorActivity.this,
                                    "修复失败:" + t.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    /** 框选覆盖层:手指拖动画一个半透明橙色选框,再拖重新选 */
    private class WatermarkRectView extends View {
        final Rect rect = new Rect();
        boolean dragging = false;

        WatermarkRectView(android.content.Context c) {
            super(c);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (!rect.isEmpty()) {
                Paint p = new Paint();
                p.setStyle(Paint.Style.FILL);
                p.setColor(0x3322B57F);
                canvas.drawRect(rect, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2);
                p.setColor(0xFF22B57F);
                canvas.drawRect(rect, p);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int x = Math.round(event.getX());
            int y = Math.round(event.getY());
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    rect.set(x, y, x, y);
                    dragging = true;
                    invalidate();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (dragging) {
                        rect.right = x;
                        rect.bottom = y;
                        invalidate();
                        return true;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    dragging = false;
                    return true;
            }
            return super.onTouchEvent(event);
        }
    }

    /** 恢复为原始照片(去水印/二次元等改动后可一键还原) */
    private void restoreOriginal() {
        if (originalSource == null || source == originalSource) {
            Toast.makeText(this, "当前已是原始照片", Toast.LENGTH_SHORT).show();
            return;
        }
        if (source != null) source.recycle();
        source = originalSource;
        btnAiRestore.setVisibility(View.GONE);
        regenerate();
    }

    // ---------------- 生成 ----------------

    private void scheduleRegen() {
        if (source == null) return;
        main.removeCallbacks(regenTask);
        main.postDelayed(regenTask, 120);
    }

    private void regenerate() {
        if (imported) {
            Toast.makeText(this, "导入的图纸不支持改参数重新生成,可继续编辑、导出",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (source == null) return;
        showLoading(true);
        final int seq = ++genSeq;
        final PatternEngine.Options opt = new PatternEngine.Options();
        opt.cols = cols;
        opt.rows = rows;
        opt.dither = dither;
        opt.brightness = brightness;
        opt.contrast = contrast;
        opt.saturation = saturation;
        opt.style = style;
        opt.brickSize = BRICK_SIZES[brickIdx];
        opt.abstractUsePalette = abstractUsePalette;
        opt.abstractColors = abstractColors;
        opt.abstractSnapToBeads = abstractSnap;
        opt.bgRemove = bgRemove;
        opt.bgTolerance = bgTolerance;
        opt.roundBoard = roundBoard;
        opt.maxColors = COLOR_LIMITS[maxColorsIdx];
        opt.dominant = dominant;
        opt.denoise = denoise;
        opt.preciseColor = preciseColor;
        final List<BeadColor> beadPalette = BeadPalettes.getPalette(tierIdx);
        exec.execute(new Runnable() {
            @Override
            public void run() {
                final BeadPattern np = PatternEngine.generate(source, beadPalette, opt);
                if (seq != genSeq) return;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        rawPattern = np;
                        pattern = PatternPatch.apply(np, editMap);
                        patternView.setPattern(pattern);
                        adapter.notifyDataSetChanged();
                        updateSummary();
                        updateEditsButton();
                        showLoading(false);
                        // 重新生成后格子变了,完成度标记失效,清空重来
                        beadDone.clear();
                        rollBeadDay();
                        beadDoneToday = 0;
                        if (beadAssist) {
                            assistFocus = pattern.usedColors.isEmpty()
                                    ? -1 : pattern.usedColors.get(0).index;
                            updateAssistUi();
                            patternView.setAssist(true, assistFocus, beadDone);
                        }
                        // 首页工具卡片带入的自动动作:图纸就绪后执行一次
                        if (pendingAction != PENDING_NONE && !blankCanvas) {
                            pendingAction = PENDING_NONE;
                            showWatermarkDialog();
                        }
                    }
                });
            }
        });
    }

    private void showLoading(boolean show) {
        showLoading(show, null);
    }

    private void showLoading(boolean show, String text) {
        loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        if (text != null && tvLoading != null) {
            tvLoading.setText(text);
        }
    }

    // ---------------- 拼豆模式(逐色辅助 + 完成度标记) ----------------

    private void setBeadAssist(boolean on) {
        beadAssist = on;
        if (on) {
            beadAssistPanel.setVisibility(View.VISIBLE);
            tvAssistProgress.setVisibility(View.VISIBLE);
            Anim.expand(beadAssistPanel);
            Anim.expand(tvAssistProgress);
            if (assistToolsRow != null) assistToolsRow.setVisibility(View.VISIBLE);
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            if (pattern != null && !pattern.usedColors.isEmpty() && assistFocus < 0) {
                assistFocus = pattern.usedColors.get(0).index;
            }
            Toast.makeText(this, "已开启:图纸页点一下 = 标记已拼好,再点取消",
                    Toast.LENGTH_LONG).show();
        } else {
            beadAssistPanel.setVisibility(View.GONE);
            tvAssistProgress.setVisibility(View.GONE);
            if (assistToolsRow != null) assistToolsRow.setVisibility(View.GONE);
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        updateAssistUi();
        patternView.setAssist(on, assistFocus, beadDone);
    }

    /** 切到下一种颜色(按用量从多到少循环) */
    /** 跨天滚动:"今日完成"计数只属于当天,隔天自动归零 */
    private void rollBeadDay() {
        String today = new java.text.SimpleDateFormat(
                "yyyy-MM-dd", Locale.CHINA).format(new java.util.Date());
        if (!today.equals(beadDoneDay)) {
            beadDoneDay = today;
            beadDoneToday = 0;
        }
    }

    /** 当前"今日完成"颗数(隔天未操作时显示 0) */
    private int todayCount() {
        String today = new java.text.SimpleDateFormat(
                "yyyy-MM-dd", Locale.CHINA).format(new java.util.Date());
        return today.equals(beadDoneDay) ? beadDoneToday : 0;
    }

    /** 逐色已拼数量(下标 = palette 下标) */
    private int[] countDonePerColor() {
        int[] out = new int[pattern.palette.size()];
        for (int k : beadDone) {
            int idx = pattern.cellAt(k % pattern.cols, k / pattern.cols);
            if (idx >= 0 && idx < out.length) out[idx]++;
        }
        return out;
    }

    private void cycleAssistColor() {
        if (pattern == null || pattern.usedColors.isEmpty()) return;
        int pos = -1;
        for (int i = 0; i < pattern.usedColors.size(); i++) {
            if (pattern.usedColors.get(i).index == assistFocus) {
                pos = i;
                break;
            }
        }
        int next = (pos + 1) % pattern.usedColors.size();
        int[] donePer = countDonePerColor();
        // 优先跳到还有剩豆的颜色,全部拼完时才纯轮转
        for (int i = 1; i < pattern.usedColors.size(); i++) {
            int cand = (pos + i) % pattern.usedColors.size();
            BeadPattern.UsedColor uc = pattern.usedColors.get(cand);
            if (pattern.counts[uc.index] - donePer[uc.index] > 0) {
                next = cand;
                break;
            }
        }
        assistFocus = pattern.usedColors.get(next).index;
        updateAssistUi();
        patternView.setAssist(true, assistFocus, beadDone);
    }

    private void updateAssistUi() {
        if (pattern == null || tvAssistColor == null) return;        if (assistFocus < 0 || assistFocus >= pattern.palette.size()) {
            tvAssistColor.setText("先生成图纸");
            return;
        }
        BeadColor c = pattern.palette.get(assistFocus);
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(0xFF000000 | c.rgb);
        assistSwatch.setBackground(gd);

        int order = 0;
        for (int i = 0; i < pattern.usedColors.size(); i++) {
            if (pattern.usedColors.get(i).index == assistFocus) {
                order = i + 1;
                break;
            }
        }
        int total = pattern.counts[assistFocus];
        int done = 0;
        for (int y = 0; y < pattern.rows; y++) {
            for (int x = 0; x < pattern.cols; x++) {
                if (pattern.cellAt(x, y) == assistFocus
                        && beadDone.contains(y * pattern.cols + x)) {
                    done++;
                }
            }
        }
        int remain = total - done;
        tvAssistColor.setText(String.format(Locale.CHINA,
                remain > 0 ? "颜色 %d/%d · 本色 %d 颗 · 剩 %d 颗"
                        : "颜色 %d/%d · 本色 %d 颗 · 🎉拼完",
                order, pattern.usedColors.size(), total, remain));
        float pct = pattern.totalBeads > 0
                ? beadDone.size() * 100f / pattern.totalBeads : 0f;
        tvAssistProgress.setText(String.format(Locale.CHINA,
                "✅ 已拼 %d/%d 颗 · 总进度 %.0f%%(%d/%d 颗) · 🔥今日 %d 颗",
                done, total, pct, beadDone.size(), pattern.totalBeads, todayCount()));
    }

    // ---------------- 豆豆清单 ----------------

    private void setupList() {
        adapter = new BeadAdapter();
        View header = getLayoutInflater().inflate(R.layout.list_header, beadList, false);
        tvSummary = header.findViewById(R.id.tvSummary);
        tvSummary.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPriceDialog();
            }
        });
        header.findViewById(R.id.btnInventory).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showInventoryDialog();
            }
        });
        Skin.apply(header);
        beadList.addHeaderView(header, null, false);
        beadList.setAdapter(adapter);
        // 长按豆单某一行 = 全局替换该色
        beadList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view,
                                           int position, long id) {
                int pos = position - beadList.getHeaderViewsCount();
                if (pos < 0 || pattern == null
                        || pos >= pattern.usedColors.size()) return false;
                showReplaceDialog(pattern.usedColors.get(pos));
                return true;
            }
        });
    }

    /**
     * 缺豆替代建议:对每种"库存不够"的用量色,在当前色板里找
     * "库存富余量 ≥ 该色需求"且 Lab 色差最近的替代色(ΔE<18 才推荐)。
     * 富余量 = 替代色库存 - 它自己在图纸里的用量(避免拆东墙补西墙)。
     */
    private void computeSubstitutes() {
        beadSubstitutes.clear();
        if (pattern == null || pattern.usedColors.isEmpty()) return;
        List<BeadColor> pal = pattern.palette;
        double[][] labs = new double[pal.size()][];
        for (int i = 0; i < pal.size(); i++) {
            labs[i] = ColorMath.rgbToLab(0xFF000000 | pal.get(i).rgb);
        }
        for (BeadPattern.UsedColor uc : pattern.usedColors) {
            int have = BeadInventory.get(this, uc.color.rgb);
            if (have < 0 || have >= uc.count) continue;   // 只管缺豆的
            int best = -1;
            double bestDe = Double.MAX_VALUE;
            for (int j = 0; j < pal.size(); j++) {
                if (j == uc.index) continue;
                int inv = BeadInventory.get(this, pal.get(j).rgb);
                if (inv < 0) continue;
                int spare = inv - pattern.counts[j];   // 排除替代色自身图纸用量
                if (spare < uc.count) continue;
                double de = ColorMath.dist2(labs[uc.index], labs[j]);
                if (de < bestDe) {
                    bestDe = de;
                    best = j;
                }
            }
            if (best >= 0 && bestDe < 18 * 18) {
                beadSubstitutes.put(uc.index, best);
            }
        }
    }

    private void updateSummary() {
        if (pattern == null) return;
        computeSubstitutes();
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.CHINA, "总用豆:%,d 颗\n", pattern.totalBeads));
        sb.append(String.format(Locale.CHINA, "颜色:%d 种 · 29×29 拼板:%d 块\n",
                pattern.usedColors.size(), pattern.boardsNeeded()));
        sb.append(String.format(Locale.CHINA, "成品约:%.0f × %.0f cm(标准 5mm 豆)",
                pattern.cols * 0.5, pattern.rows * 0.5));
        if (pattern.emptyCount > 0) {
            sb.append(String.format(Locale.CHINA, "\n空格:%,d 格(不放置)", pattern.emptyCount));
        }
        // 克重与成本估算(标准 5mm 豆约 0.024g/颗;单价可在设置里改)
        sb.append(String.format(Locale.CHINA, "\n约重 %,d g · 参考成本 ¥%.2f(点此改单价)",
                Math.round(pattern.totalBeads * 0.024f),
                pattern.totalBeads * beadUnitPrice()));
        // 豆仓缺口:只在登记过至少一种颜色时显示
        int registered = 0;
        int enough = 0;
        int shortage = 0;
        for (BeadPattern.UsedColor uc : pattern.usedColors) {
            int have = BeadInventory.get(this, uc.color.rgb);
            if (have < 0) continue;
            registered++;
            if (have >= uc.count) {
                enough++;
            } else {
                shortage += uc.count - have;
            }
        }
        if (registered > 0) {
            sb.append(String.format(Locale.CHINA,
                    "\n🎒 豆仓:%d/%d 色够用 · 还需补 %,d 颗",
                    enough, pattern.usedColors.size(), shortage));
        }
        if (!beadDone.isEmpty()) {
            sb.append(String.format(Locale.CHINA,
                    "\n🧩 已拼 %,d/%,d 颗 · 今日完成 %d 颗",
                    beadDone.size(), pattern.totalBeads, todayCount()));
        }
        tvSummary.setText(sb.toString());
    }

    /** 全局替换:长按豆单某一行,把该色所有格子一键换成另一个已用色 */
    private void showReplaceDialog(final BeadPattern.UsedColor from) {
        if (pattern == null || pattern.usedColors.size() < 2) {
            Toast.makeText(this, "图纸里只有一种颜色,没得换", Toast.LENGTH_SHORT).show();
            return;
        }
        final List<BeadPattern.UsedColor> choices = new ArrayList<>();
        for (BeadPattern.UsedColor uc : pattern.usedColors) {
            if (uc.index != from.index) choices.add(uc);
        }
        String[] labels = new String[choices.size()];
        for (int i = 0; i < choices.size(); i++) {
            labels[i] = choices.get(i).color.fullLabel()
                    + String.format(Locale.CHINA, "(已有 %,d 颗)", choices.get(i).count);
        }
        new AlertDialog.Builder(this)
                .setTitle(String.format(Locale.CHINA, "把「%s」的 %,d 颗全部换成:",
                        from.color.fullLabel(), from.count))
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        replaceColorEverywhere(from.index, choices.get(which).index);
                    }
                })
                .show();
    }

    private void replaceColorEverywhere(int fromIdx, int toIdx) {
        if (pattern == null || rawPattern == null) return;
        pushUndoState();
        int changed = 0;
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                if (pattern.cellAt(x, y) != fromIdx) continue;
                int key = y * cols + x;
                Integer base = rawPattern.cellAt(x, y);
                if (base != null && base.intValue() == toIdx) {
                    editMap.remove(key);   // 与自动结果一致就无需覆盖记录
                } else {
                    editMap.put(key, toIdx);
                }
                changed++;
            }
        }
        applyEditsToUi();
        updateEditsButton();
        Toast.makeText(this,
                String.format(Locale.CHINA, "已替换 %d 格,点 ↺ 可回退", changed),
                Toast.LENGTH_SHORT).show();
    }

    /** 豆仓管理行:一种颜色 + 手头数量草稿 */
    private static final class InvRow {
        final BeadColor color;
        String draft;

        InvRow(BeadColor c, int value) {
            color = c;
            draft = value < 0 ? "" : String.valueOf(value);
        }
    }

    /** 豆仓管理弹窗:当前色板逐色登记手头数量,保存后豆单自动标缺口 */
    private void showInventoryDialog() {
        final List<BeadColor> pal = BeadPalettes.getPalette(tierIdx);
        final List<InvRow> rows = new ArrayList<>();
        for (BeadColor c : pal) {
            rows.add(new InvRow(c, BeadInventory.get(this, c.rgb)));
        }

        ListView lv = new ListView(this);
        lv.setDivider(null);
        BaseAdapter invAdapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return rows.size();
            }

            @Override
            public Object getItem(int position) {
                return rows.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                LinearLayout row = new LinearLayout(EditorActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                int pad = Math.round(10 * getResources().getDisplayMetrics().density);
                row.setPadding(pad, pad / 2, pad, pad / 2);

                InvRow r = rows.get(position);

                View sw = new View(EditorActivity.this);
                GradientDrawable gd = new GradientDrawable();
                gd.setShape(GradientDrawable.OVAL);
                gd.setColor(0xFF000000 | r.color.rgb);
                sw.setBackground(gd);
                row.addView(sw, new LinearLayout.LayoutParams(
                        Math.round(26 * getResources().getDisplayMetrics().density),
                        Math.round(26 * getResources().getDisplayMetrics().density)));

                TextView name = new TextView(EditorActivity.this);
                name.setText(r.color.fullLabel());
                name.setTextColor(0xFF1F2430);
                name.setTextSize(13);
                LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                nlp.leftMargin = pad;
                row.addView(name, nlp);

                final EditText et = new EditText(EditorActivity.this);
                et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
                et.setText(r.draft);
                et.setHint("0");
                et.setTextSize(13);
                et.setTag(position);
                et.addTextChangedListener(new android.text.TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int st, int b, int a) {
                    }

                    @Override
                    public void onTextChanged(CharSequence s, int st, int b, int a) {
                    }

                    @Override
                    public void afterTextChanged(android.text.Editable s) {
                        Object tag = et.getTag();
                        if (tag instanceof Integer) {
                            rows.get((Integer) tag).draft = s.toString();
                        }
                    }
                });
                LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                        Math.round(88 * getResources().getDisplayMetrics().density),
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                row.addView(et, elp);
                return row;
            }
        };
        lv.setAdapter(invAdapter);

        new AlertDialog.Builder(this)
                .setTitle("🎒 豆仓库存(当前色板 " + pal.size() + " 色)")
                .setView(lv)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        for (InvRow r : rows) {
                            try {
                                String t = r.draft.trim();
                                if (t.isEmpty()) continue;
                                BeadInventory.set(EditorActivity.this,
                                        r.color.rgb, Integer.parseInt(t));
                            } catch (NumberFormatException ignored) {
                            }
                        }
                        adapter.notifyDataSetChanged();
                        updateSummary();
                        Toast.makeText(EditorActivity.this,
                                "豆仓已更新,豆单已标注缺口", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private float beadUnitPrice() {
        return getSharedPreferences("pindou", MODE_PRIVATE).getFloat("bead_price", 0.02f);
    }

    /** 修改每颗豆单价,豆单里的参考成本随之刷新 */
    private void showPriceDialog() {
        final EditText et = new EditText(this);
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        et.setText(String.valueOf(beadUnitPrice()));
        new AlertDialog.Builder(this)
                .setTitle("每颗豆单价(元)")
                .setView(et)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        try {
                            float p = Float.parseFloat(et.getText().toString().trim());
                            if (p >= 0 && p < 100) {
                                getSharedPreferences("pindou", MODE_PRIVATE)
                                        .edit().putFloat("bead_price", p).apply();
                                updateSummary();
                                Toast.makeText(EditorActivity.this,
                                        "已更新单价", Toast.LENGTH_SHORT).show();
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private class BeadAdapter extends BaseAdapter {

        private final LayoutInflater inflater;
        /** 逐色已拼缓存(null = 没有任何完成标记),随 notifyDataSetChanged 重算 */
        private int[] donePerColor;

        BeadAdapter() {
            inflater = LayoutInflater.from(EditorActivity.this);
        }

        @Override
        public void notifyDataSetChanged() {
            donePerColor = beadDone.isEmpty() ? null : countDonePerColor();
            super.notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return pattern == null ? 0 : pattern.usedColors.size();
        }

        @Override
        public Object getItem(int position) {
            return pattern.usedColors.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = inflater.inflate(R.layout.item_bead_count, parent, false);
                Skin.apply(v);
            }
            BeadPattern.UsedColor uc = pattern.usedColors.get(position);

            View swatch = v.findViewById(R.id.swatch);
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            gd.setColor(0xFF000000 | uc.color.rgb);
            swatch.setBackground(gd);

            TextView sym = v.findViewById(R.id.tvSymbol);
            sym.setText(uc.symbol);
            sym.setTextColor(ColorMath.textColorOn(uc.color.rgb));

            TextView name = v.findViewById(R.id.tvName);
            name.setText(uc.color.fullLabel());

            TextView brand = v.findViewById(R.id.tvBrand);
            if (uc.color.hasOfficialCode()) {
                brand.setText("官方色号,可直接照单");
            } else {
                String bt = BeadBrand.tagOf(uc.color.rgb);
                brand.setText(bt.isEmpty() ? "暂无对照" : "≈ " + bt);
            }

            TextView count = v.findViewById(R.id.tvCount);
            if (donePerColor != null && donePerColor[uc.index] > 0) {
                int left = uc.count - donePerColor[uc.index];
                count.setText(String.format(Locale.CHINA, left > 0
                        ? "%,d 颗 · 剩 %d" : "%,d 颗 · ✅拼完", uc.count, left));
            } else {
                count.setText(String.format(Locale.CHINA, "%,d 颗", uc.count));
            }

            TextView percent = v.findViewById(R.id.tvPercent);
            float pct = pattern.totalBeads > 0
                    ? uc.count * 100f / pattern.totalBeads : 0f;
            percent.setText(String.format(Locale.CHINA, "%.1f%%", pct));

            // 豆仓库存状态:够 / 缺 / 未登记
            TextView inv = v.findViewById(R.id.tvInv);
            int have = BeadInventory.get(EditorActivity.this, uc.color.rgb);
            if (have < 0) {
                inv.setText("库存未登记");
                inv.setTextColor(0xFF8A8F98);
            } else if (have >= uc.count) {
                inv.setText("✔ 库存够·余 " + (have - uc.count));
                inv.setTextColor(0xFF22B57F);
            } else {
                inv.setText("缺 " + (uc.count - have) + " 颗");
                inv.setTextColor(0xFFF0654E);
            }

            // 缺豆替代建议(豆仓登记过才会出现)
            TextView sub = v.findViewById(R.id.tvSub);
            Integer subIdx = beadSubstitutes.get(uc.index);
            if (subIdx != null && subIdx < pattern.palette.size()) {
                BeadColor sc = pattern.palette.get(subIdx);
                sub.setText("💡 可用「" + sc.fullLabel() + "」代替");
                sub.setVisibility(View.VISIBLE);
            } else {
                sub.setVisibility(View.GONE);
            }

            ProgressBar pb = v.findViewById(R.id.pb);
            pb.setMax(100);
            pb.setProgress((int) pct);
            pb.setProgressTintList(
                    ColorStateList.valueOf(0xFF000000 | uc.color.rgb));
            return v;
        }
    }

    // ---------------- 导出 / 分享 ----------------

    private void syncBgUi() {
        if (sbBgTol == null) return;
        sbBgTol.setProgress(bgTolerance);
        tvBgTol.setText(bgTolerance + "%");
    }

    /** 用引擎原始输出叠加手动覆盖,刷新展示与清单 */
    private void applyEditsToUi() {
        pattern = PatternPatch.apply(rawPattern, editMap);
        patternView.setPattern(pattern);
        adapter.notifyDataSetChanged();
        updateSummary();
    }

    private void updateEditsButton() {
        btnClearEdits.setVisibility(editMap.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** 弹出选色对话框:恢复自动匹配 / 已用颜色 / 全部色板 */
    private void showColorPicker(final int cellIndex) {
        int densityPad = Math.round(12 * getResources().getDisplayMetrics().density);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        ScrollView sc = new ScrollView(this);
        sc.addView(box);

        final AlertDialog[] holder = new AlertDialog[1];

        // 行构造:小圆片 + 文本
        class Row {
            void add(String text, final Integer target, int rgb, boolean bold) {
                LinearLayout row = new LinearLayout(EditorActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(densityPad, densityPad / 2, densityPad, densityPad / 2);
                row.setClickable(true);
                row.setBackgroundResource(android.R.drawable.list_selector_background);

                View swatch = new View(EditorActivity.this);
                GradientDrawable gd = new GradientDrawable();
                gd.setShape(GradientDrawable.OVAL);
                gd.setColor(rgb == -1 ? 0xFFEFEFEF : (0xFF000000 | rgb));
                swatch.setBackground(gd);
                LinearLayout.LayoutParams sw = new LinearLayout.LayoutParams(
                        dpCell(), dpCell());
                sw.rightMargin = densityPad * 2 / 3;
                row.addView(swatch, sw);

                TextView t = new TextView(EditorActivity.this);
                t.setText(text);
                t.setTextColor(target == null ? 0xFF212121 : ColorMath.textColorOn(rgb));
                t.setTextSize(14);
                if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
                row.addView(t, new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

                row.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (holder[0] != null) holder[0].dismiss();
                        if (target == null) {
                            editMap.remove(cellIndex);
                        } else {
                            editMap.put(cellIndex, target);
                        }
                        applyEditsToUi();
                        updateEditsButton();
                    }
                });
                box.addView(row, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
            }

            void addHeader(String title) {
                TextView h = new TextView(EditorActivity.this);
                h.setText(title);
                h.setTextColor(0xFF1F2430);
                h.setTextSize(12);
                h.setTypeface(null, android.graphics.Typeface.BOLD);
                h.setPadding(densityPad / 2, densityPad, densityPad / 2, densityPad / 4);
                box.addView(h, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
            }
        }

        Row r = new Row();

        r.addHeader("当前格 " + (cellIndex % pattern.cols + 1) + "," + (cellIndex / pattern.cols + 1));
        int curIdx = pattern.cells[cellIndex];
        String autoLabel = curIdx >= 0 ? "自动匹配的是 "
                + rawPaletteName(curIdx) : "该格当前为空";
        r.add("↺ 恢复自动匹配" + (curIdx >= 0 ? "(" + autoLabel + ")" : ""),
                null, curIdx >= 0 ? pattern.palette.get(curIdx).rgb : 0xFFCCCCCC, true);

        r.addHeader("已用颜色");
        for (final BeadPattern.UsedColor uc : pattern.usedColors) {
            r.add(uc.symbol + "  " + uc.color.fullLabel(),
                    uc.index, uc.color.rgb, false);
        }

        r.addHeader("全部色板");
        List<BeadColor> pal = pattern.palette;
        for (int i = 0; i < pal.size(); i++) {
            final int idx = i;
            r.add(pal.get(i).fullLabel(), idx, pal.get(i).rgb, false);
        }

        holder[0] = new AlertDialog.Builder(this)
                .setTitle("选择这一格的豆色")
                .setView(sc)
                .setNegativeButton("取消", null)
                .create();
        holder[0].show();
    }

    private int dpCell() {
        return Math.round(24 * getResources().getDisplayMetrics().density);
    }

    private String rawPaletteName(int idx) {
        if (rawPattern == null || idx < 0 || idx >= rawPattern.palette.size()) return "";
        return rawPattern.palette.get(idx).fullLabel();
    }

    /** 导出可分享的图纸文件(.json,自含色板,规范见 docs/SHARE-FORMAT.md) */
    private void exportFile() {
        showLoading(true, "正在打包图纸文件…");
        exec.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String stamp = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.CHINA)
                            .format(new Date());
                    final String fileName = "拼豆图纸_" + pattern.cols + "x" + pattern.rows
                            + "_" + stamp + ".json";
                    JSONObject o = PatternShare.build(pattern, fileName.substring(0,
                            fileName.lastIndexOf('_')));
                    File out = new File(getCacheDir(), fileName);
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
                    fos.write(o.toString().getBytes("UTF-8"));
                    fos.close();
                    final Uri uri = AppFileProvider.forCacheShare(out);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showLoading(false);
                            share(uri, "application/json");
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showLoading(false);
                            Toast.makeText(EditorActivity.this,
                                    "导出失败:" + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        });
    }

    /** 从文件选择器导入分享图纸 */
    private void importFile() {
        try {
            android.content.Intent it =
                    new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
            it.addCategory(android.content.Intent.CATEGORY_OPENABLE);
            it.setType("*/*");
            startActivityForResult(it, REQ_IMPORT);
        } catch (Throwable t) {
            Toast.makeText(this, "无法打开文件选择器", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_IMPORT && resultCode == RESULT_OK
                && data != null && data.getData() != null) {
            importFromUri(data.getData());
        }
    }

    private void importFromUri(final Uri uri) {
        exec.execute(new Runnable() {
            @Override
            public void run() {
                String hint = null;
                BeadPattern bp = null;
                try {
                    java.io.InputStream is = getContentResolver().openInputStream(uri);
                    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
                    is.close();
                    JSONObject o = new JSONObject(new String(bos.toByteArray(), "UTF-8"));
                    bp = PatternShare.parse(o);
                } catch (Exception e) {
                    hint = e.getMessage();
                }
                final BeadPattern got = bp;
                final String err = hint;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (got == null) {
                            Toast.makeText(EditorActivity.this,
                                    "导入失败:" + err, Toast.LENGTH_LONG).show();
                            return;
                        }
                        applyImportedPattern(got);
                    }
                });
            }
        });
    }

    /** 把导入的图纸装进编辑器:可编辑/导出/拼豆辅助,但不支持重新生成 */
    private void applyImportedPattern(BeadPattern bp) {
        imported = true;
        rawPattern = bp;
        pattern = bp;
        source = null;
        originalSource = null;
        blankCanvas = true;
        hidePhotoOnlyCards();
        cols = bp.cols;
        rows = bp.rows;
        syncSizeUi();
        editMap.clear();
        beadDone.clear();
        rollBeadDay();
        beadDoneToday = 0;
        undoStack.clear();
        redoStack.clear();
        updateUndoRedoButtons();
        patternView.setPattern(pattern);
        adapter.notifyDataSetChanged();
        updateSummary();
        updateEditsButton();
        selectTab(1);
        Toast.makeText(this, "已导入图纸,可以继续编辑、导出或按色拼豆",
                Toast.LENGTH_SHORT).show();
    }

    /** 定位本色第一颗未拼的格子:居中显示并闪烁提示(拼豆模式) */
    private void locateAssistUndone() {
        if (pattern == null || assistFocus < 0) return;
        for (int y = 0; y < pattern.rows; y++) {
            for (int x = 0; x < pattern.cols; x++) {
                if (pattern.cellAt(x, y) != assistFocus) continue;
                if (!beadDone.contains(y * pattern.cols + x)) {
                    patternView.centerOn(x, y);
                    patternView.flashCell(x, y);
                    return;
                }
            }
        }
        Toast.makeText(this, "🎉 本色已经全部拼完了", Toast.LENGTH_SHORT).show();
    }

    /** 打卡日历:按月查看每天完成的颗数(全局记录,纯本地) */
    private void showCalendarDialog() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        showCalendarDialog(cal.get(java.util.Calendar.YEAR),
                cal.get(java.util.Calendar.MONTH));
    }

    private void showCalendarDialog(final int year, final int month) {
        float dm = getResources().getDisplayMetrics().density;
        int pad = Math.round(10 * dm);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad, pad, 0);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView prev = calChip("◀");
        TextView next = calChip("▶");
        TextView title = new TextView(this);
        title.setPadding(Math.round(14 * dm), 0, Math.round(14 * dm), 0);
        title.setText(String.format(Locale.CHINA, "%d 年 %d 月", year, month + 1));
        title.setTextColor(0xFF232323);
        title.setTextSize(16);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        head.addView(prev);
        head.addView(title);
        head.addView(next);
        box.addView(head);

        String[] week = {"一", "二", "三", "四", "五", "六", "日"};
        LinearLayout weekRow = new LinearLayout(this);
        weekRow.setOrientation(LinearLayout.HORIZONTAL);
        weekRow.setPadding(0, Math.round(8 * dm), 0, 0);
        for (String w : week) {
            TextView tv = new TextView(this);
            tv.setText(w);
            tv.setTextSize(11);
            tv.setTextColor(0xFF8A8F98);
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            weekRow.addView(tv);
        }
        box.addView(weekRow);

        java.util.Calendar first = java.util.Calendar.getInstance();
        first.set(year, month, 1, 12, 0, 0);
        int offset = (first.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7;   // 周一为第一列
        int daysInMonth = first.getActualMaximum(java.util.Calendar.DAY_OF_MONTH);
        java.util.Calendar nowC = java.util.Calendar.getInstance();
        boolean thisMonth = nowC.get(java.util.Calendar.YEAR) == year
                && nowC.get(java.util.Calendar.MONTH) == month;
        int today = nowC.get(java.util.Calendar.DAY_OF_MONTH);

        LinearLayout row = new LinearLayout(this);
        box.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        int slot = 0;
        for (int i = 0; i < offset; i++, slot++) {
            TextView blank = new TextView(this);
            blank.setLayoutParams(new LinearLayout.LayoutParams(0,
                    Math.round(46 * dm), 1f));
            row.addView(blank);
        }
        for (int d = 1; d <= daysInMonth; d++, slot++) {
            if (slot % 7 == 0) {
                row = new LinearLayout(this);
                box.addView(row, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
            }
            String day = String.format(Locale.CHINA, "%04d-%02d-%02d", year, month + 1, d);
            int cnt = BeadCalendar.get(this, day);
            TextView cell = new TextView(this);
            cell.setGravity(android.view.Gravity.CENTER);
            cell.setText(cnt > 0 ? d + "\n🔥" + cnt : String.valueOf(d));
            cell.setTextSize(10);
            if (thisMonth && d == today) {
                cell.setTextColor(0xFF1E88E5);
                cell.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            } else {
                cell.setTextColor(cnt > 0 ? 0xFFE65100 : 0xFFB9BEC5);
            }
            cell.setLayoutParams(new LinearLayout.LayoutParams(0,
                    Math.round(46 * dm), 1f));
            row.addView(cell);
        }

        android.widget.ScrollView sc = new android.widget.ScrollView(this);
        sc.addView(box);
        calendarDialog = new AlertDialog.Builder(this)
                .setTitle("📅 拼豆打卡日历")
                .setMessage("每天完成的颗数(所有项目合计),纯本地记录")
                .setView(sc)
                .setPositiveButton("关闭", null)
                .show();
        prev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (calendarDialog != null) calendarDialog.dismiss();
                int m = month - 1, y = year;
                if (m < 0) {
                    m = 11;
                    y--;
                }
                showCalendarDialog(y, m);
            }
        });
        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (calendarDialog != null) calendarDialog.dismiss();
                int m = month + 1, y = year;
                if (m > 11) {
                    m = 0;
                    y++;
                }
                showCalendarDialog(y, m);
            }
        });
    }

    private TextView calChip(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(14);
        int cp = Math.round(12 * getResources().getDisplayMetrics().density);
        tv.setPadding(cp, 0, cp, 0);
        tv.setTextColor(0xFF444444);
        tv.setClickable(true);
        return tv;
    }

    private void showExportMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, EXP_SHEET, 1, "保存图纸图片(可打印)");
        menu.getMenu().add(0, EXP_EFFECT, 2, "保存效果图");
        menu.getMenu().add(0, EXP_SHARE, 3, "分享图纸");
        menu.getMenu().add(0, EXP_PDF, 4, "导出 PDF 文档");
        menu.getMenu().add(0, EXP_FILE, 5, "导出图纸文件(.json,发给别人导入)");
        menu.getMenu().add(0, 7, 6, "📂 导入图纸文件(.json)");
        menu.getMenu().add(1, 5, 7, "💾 保存项目存档");
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(android.view.MenuItem item) {
                if (item.getItemId() == 5) {
                    saveProjectDialog();
                } else if (item.getItemId() == 7) {
                    importFile();
                } else {
                    export(item.getItemId());
                }
                return true;
            }
        });
        menu.show();
    }

    private void export(int what) {
        if (pattern == null) {
            Toast.makeText(this, "图纸还没生成好,稍等一下", Toast.LENGTH_SHORT).show();
            return;
        }
        if (what == EXP_PDF) {
            // PDF 写到应用缓存再分享,不需要存储权限
            exportPdf();
            return;
        }
        if (what == EXP_FILE) {
            // 分享 JSON 同样写缓存,不需要存储权限
            exportFile();
            return;
        }
        if (Build.VERSION.SDK_INT < 29 && checkSelfPermission(
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            pendingExport = what;
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQ_STORAGE);
            return;
        }
        doExport(what);
    }

    /** 渲染大图 -> 切 A4 多页 PDF -> 弹分享 */
    private void exportPdf() {
        showLoading(true, "正在生成 PDF…");
        exec.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Bitmap sheet = PatternSheetRenderer.render(pattern,
                            currentPaletteName());
                    String stamp = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.CHINA)
                            .format(new Date());
                    String name = "拼豆图纸_" + pattern.cols + "x" + pattern.rows
                            + "_" + stamp + ".pdf";
                    final Uri uri = PdfExporter.export(EditorActivity.this, sheet,
                            pattern, currentPaletteName(), name);
                    sheet.recycle();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showLoading(false);
                            share(uri, "application/pdf");
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showLoading(false);
                            Toast.makeText(EditorActivity.this,
                                    "PDF 导出失败:" + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        });
    }

    /** 导出图纸标题里显示的色板名 */
    private String currentPaletteName() {
        if (style == PatternEngine.STYLE_ABSTRACT) {
            String deg = BRICK_LABELS[Math.max(0, Math.min(3, brickIdx))]
                    + "(" + BRICK_SIZES[brickIdx] + "×" + BRICK_SIZES[brickIdx] + ")";
            String colorPart = abstractUsePalette
                    ? "主色" + abstractColors
                    + (abstractSnap ? "·吸附" + BeadPalettes.tierName(tierIdx) : "·原色")
                    : "整套" + BeadPalettes.tierName(tierIdx);
            return "抽象·" + deg + "·" + colorPart;
        }
        return BeadPalettes.tierName(tierIdx);
    }

    private void doExport(final int what) {
        showLoading(true);
        exec.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Bitmap bmp;
                    if (what == 2) {
                        bmp = EffectRenderer.render(pattern);
                    } else {
                        bmp = PatternSheetRenderer.render(pattern, currentPaletteName());
                    }
                    String stamp = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.CHINA)
                            .format(new Date());
                    String name = (what == 2 ? "拼豆效果图_" : "拼豆图纸_")
                            + pattern.cols + "x" + pattern.rows + "_" + stamp + ".png";
                    final Uri uri = GallerySaver.save(EditorActivity.this, bmp, name);
                    bmp.recycle();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showLoading(false);
                            if (what == EXP_SHARE) {
                                share(uri, "image/png");
                            } else {
                                Toast.makeText(EditorActivity.this,
                                        "已保存到相册 Pictures/" + GallerySaver.DIR_NAME,
                                        Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showLoading(false);
                            Toast.makeText(EditorActivity.this,
                                    "保存失败:" + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        });
    }

    private void share(Uri uri, String mime) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType(mime);
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(send, "分享拼豆图纸"));
        } catch (Exception e) {
            Toast.makeText(this, "分享失败", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------- 空白画布 ----------------

    /** 进入空白画布:没有源照片,直接在格子上手绘 */
    private void startBlankCanvas() {
        blankCanvas = true;
        imported = false;
        cols = rows = 29;          // 默认一块标准板,适合挂件
        hidePhotoOnlyCards();
        setPaintMode(true, true);  // 进来就能直接画
        ensureBrushDefault();
        rebuildBlankRaw();
        selectTab(1);              // 直接停在图纸页
        Toast.makeText(this, "空白画布已就绪,滑动即可涂色 🎨",
                Toast.LENGTH_SHORT).show();
    }

    /** 空白画布模式下隐藏与照片相关的卡片 */
    private void hidePhotoOnlyCards() {
        preprocessCardWrap.setVisibility(View.GONE);
    }

    private BeadPattern emptyPattern(int c, int r) {
        List<BeadColor> pal = BeadPalettes.getPalette(tierIdx);
        int[] cells = new int[c * r];
        Arrays.fill(cells, -1);
        List<BeadPattern.UsedColor> used = new ArrayList<>();
        return new BeadPattern(c, r, pal, cells,
                new int[Math.max(1, pal.size())], used, 0, c * r);
    }

    /** 用当前尺寸重建全空图纸(清掉引擎旧结果,手动修改由 editMap 叠加) */
    private void rebuildBlankRaw() {
        rawPattern = emptyPattern(cols, rows);
        pattern = PatternPatch.apply(rawPattern, editMap);
        patternView.setPattern(pattern);
        adapter.notifyDataSetChanged();
        updateSummary();
        updateEditsButton();
    }

    // ---------------- 画笔模式 ----------------

    /**
     * 开关画笔。静默参数用于程序化切换(初始化/重置),
     * 不弹提示也不强制切标签页。
     */
    private void setPaintMode(boolean on, boolean silent) {
        paintMode = on;
        if (swPaint != null && swPaint.isChecked() != on) {
            swPaint.setOnCheckedChangeListener(null);
            swPaint.setChecked(on);
            swPaint.setOnCheckedChangeListener(new Switch.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    setPaintMode(isChecked, false);
                }
            });
        }
        if (on && editCell) {
            editCell = false;
            swEditCell.setOnCheckedChangeListener(null);
            swEditCell.setChecked(false);
            swEditCell.setOnCheckedChangeListener(new Switch.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    editCell = isChecked;
                    if (isChecked && paintMode) {
                        setPaintMode(false, true);
                    }
                    Toast.makeText(EditorActivity.this,
                            isChecked ? "已开启:去「图纸」页点任意格子换色"
                                    : "已关闭点格修改",
                            Toast.LENGTH_SHORT).show();
                }
            });
        }
        if (on && beadAssist) {
            swBeadAssist.setOnCheckedChangeListener(null);
            swBeadAssist.setChecked(false);
            swBeadAssist.setOnCheckedChangeListener(beadAssistListener);
            setBeadAssist(false);
        }
        brushPanel.setVisibility(on ? View.VISIBLE : View.GONE);
        if (on && !silent) Anim.expand(brushPanel);
        patternView.setPaintEnabled(on);
        if (on && !silent) {
            selectTab(1);
            if (rawPattern == null && !blankCanvas) {
                Toast.makeText(this, "等图纸生成好后就可以涂色了",
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this,
                        eraseOn ? "橡皮已选好,滑过即可擦除,长按可整片擦除"
                                : "在图纸上滑动涂色,长按一格可整片填充;点「换一支」可选颜色",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    /** 没选过画笔时给个默认色(黑色豆),避免第一笔没颜色 */
    private void ensureBrushDefault() {
        if (brushPalIdx >= 0) return;
        List<BeadColor> pal = BeadPalettes.getPalette(tierIdx);
        for (int i = 0; i < pal.size(); i++) {
            if (pal.get(i).rgb == 0x141414) {   // 经典黑
                brushPalIdx = i;
                return;
            }
        }
        brushPalIdx = 0;
    }

    /** 单格落笔;连续滑动时先记账,按帧合并刷新一次界面 */
    private void paintAt(int x, int y) {
        if (!paintMode || pattern == null || rawPattern == null) return;
        if (x < 0 || y < 0 || x >= cols || y >= rows) return;
        if (pattern.outsideShape(x, y)) return;   // 圆形板板外无格
        int target = eraseOn ? -1 : brushPalIdx;
        if (target < -1) return;
        boolean changed = applyBrush(x, y, target);
        if (paintMirror) {
            int mx = cols - 1 - x;
            if (mx != x && mx >= 0 && !pattern.outsideShape(mx, y)) {
                changed |= applyBrush(mx, y, target);
            }
        }
        if (changed) queuePaintFlush();
    }

    /** 在一个格子落笔(镜像是同一支笔);返回是否真的改动 */
    private boolean applyBrush(int x, int y, int target) {
        int idx = y * cols + x;
        Integer cur = editMap.get(idx);
        if (cur != null && cur.intValue() == target) return false;
        Integer base = rawPattern.cellAt(x, y);
        if (base != null && base.intValue() == target) {
            // 与自动结果相同:删掉覆盖记录即可,但只要真有变动就记一笔撤销
            if (editMap.containsKey(idx)) {
                if (strokeSnapPending) {
                    pushUndoState();
                    strokeSnapPending = false;
                }
                editMap.remove(idx);
                return true;
            }
            return false;
        }
        if (strokeSnapPending) {
            pushUndoState();          // 一笔 = 一条撤销记录
            strokeSnapPending = false;
        }
        editMap.put(idx, target);
        return true;
    }

    /** 油漆桶:把与落点同色的四连通区域整体换成画笔色(橡皮 = 整片挖空) */
    private void floodFill(int sx, int sy) {
        if (!paintMode || pattern == null || rawPattern == null) return;
        if (pattern.outsideShape(sx, sy)) return;
        int from = pattern.cellAt(sx, sy);
        if (from < 0) return;                    // 空格不做填充起点
        int target = eraseOn ? -1 : brushPalIdx;
        if (target < -1 || target == from) return;
        boolean[] seen = new boolean[cols * rows];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        int start = sy * cols + sx;
        queue.add(start);
        seen[start] = true;
        boolean snapPushed = false;              // 一次填充 = 一条撤销记录
        int filled = 0;
        while (!queue.isEmpty()) {
            int key = queue.removeFirst();
            int cx = key % cols;
            int cy = key / cols;
            Integer base = rawPattern.cellAt(cx, cy);
            if (base != null && base.intValue() == target) {
                if (editMap.remove(key) != null) {
                    if (!snapPushed) { pushUndoState(); snapPushed = true; }
                    filled++;
                }
            } else {
                if (!snapPushed) { pushUndoState(); snapPushed = true; }
                editMap.put(key, target);
                filled++;
            }
            for (int d = 0; d < 4; d++) {
                int nx = cx + (d == 0 ? 1 : d == 1 ? -1 : 0);
                int ny = cy + (d == 2 ? 1 : d == 3 ? -1 : 0);
                if (nx < 0 || ny < 0 || nx >= cols || ny >= rows) continue;
                int nk = ny * cols + nx;
                if (seen[nk] || pattern.outsideShape(nx, ny)) continue;
                if (pattern.cellAt(nx, ny) == from) {
                    seen[nk] = true;
                    queue.add(nk);
                }
            }
        }
        if (filled > 0) {
            applyEditsToUi();
            updateEditsButton();
            Toast.makeText(this, String.format(Locale.CHINA, "油漆桶:已填充 %d 格", filled),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void queuePaintFlush() {
        if (paintFlushQueued) return;
        paintFlushQueued = true;
        main.post(new Runnable() {
            @Override
            public void run() {
                paintFlushQueued = false;
                applyEditsToUi();
                updateEditsButton();
            }
        });
    }

    /** 刷新画笔行的颜色块 / 名称 / 橡皮选中态 */
    private void syncBrushUi() {
        btnBrushEraser.setSelected(eraseOn);
        if (btnBrushMirror != null) btnBrushMirror.setSelected(paintMirror);
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        String label;
        if (eraseOn) {
            gd.setColor(0xFFEFEAE3);
            label = "橡皮擦(擦成空格)";
        } else {
            ensureBrushDefault();
            BeadColor c = currentPatternPalette().get(
                    Math.min(brushPalIdx, currentPatternPalette().size() - 1));
            gd.setColor(0xFF000000 | c.rgb);
            label = c.name + "(" + c.code + "号)";
        }
        brushSwatch.setBackground(gd);
        ((TextView) brushName).setText(label);
    }

    /** 当前生效的色板(抽象模式是提取后的子集) */
    private List<BeadColor> currentPatternPalette() {
        if (pattern != null && pattern.palette != null
                && !pattern.palette.isEmpty()) {
            return pattern.palette;
        }
        return BeadPalettes.getPalette(tierIdx);
    }

    /** 选画笔颜色:列出当前图纸生效色板 */
    private void showBrushPicker() {
        if (pattern == null || pattern.palette == null || pattern.palette.isEmpty()) {
            Toast.makeText(this, "先等图纸生成好", Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        ScrollView sc = new ScrollView(this);
        sc.addView(box);
        final AlertDialog[] holder = new AlertDialog[1];

        TextView tip = new TextView(this);
        tip.setText("点任意颜色设为画笔");
        tip.setTextColor(0xFF22B57F);
        tip.setTextSize(12);
        int pad = Math.round(12 * getResources().getDisplayMetrics().density);
        tip.setPadding(pad, pad, pad, pad / 4);
        box.addView(tip);

        List<BeadColor> pal = pattern.palette;
        for (int i = 0; i < pal.size(); i++) {
            final int idx = i;
            BeadColor c = pal.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(pad, pad / 2, pad, pad / 2);
            row.setClickable(true);
            row.setBackgroundResource(android.R.drawable.list_selector_background);

            View swatch = new View(this);
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            gd.setColor(0xFF000000 | c.rgb);
            swatch.setBackground(gd);
            LinearLayout.LayoutParams lp =
                    new LinearLayout.LayoutParams(dpCell(), dpCell());
            lp.rightMargin = pad * 2 / 3;
            row.addView(swatch, lp);

            TextView t = new TextView(this);
            t.setTextColor(ColorMath.textColorOn(c.rgb));
            t.setText(c.fullLabel());
            t.setTextSize(14);
            row.addView(t, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    brushPalIdx = idx;
                    eraseOn = false;
                    syncBrushUi();
                    if (holder[0] != null) holder[0].dismiss();
                    Toast.makeText(EditorActivity.this, "已换画笔 🖌",
                            Toast.LENGTH_SHORT).show();
                }
            });
            box.addView(row);
        }

        holder[0] = new AlertDialog.Builder(this)
                .setTitle("选择画笔颜色")
                .setView(sc)
                .setNegativeButton("取消", null)
                .create();
        holder[0].show();
    }

    // ---------------- 照片变换(镜像/旋转) ----------------

    private static final class Transform {
        static final int MIRROR_H = 0;
        static final int MIRROR_V = 1;
        static final int ROTATE_90 = 2;
    }

    /**
     * 对当前源照片做变换并重新生成;手动修格会跟着几何映射,
     * 所以修好的图案不会被转丢。
     */
    private void transformSource(final int kind) {
        if (aiRunning) return;
        if (!blankCanvas && source == null) return;
        final int oc = cols, orow = rows;
        exec.execute(new Runnable() {
            @Override
            public void run() {
                Bitmap nb = null;
                if (!blankCanvas && source != null && !source.isRecycled()) {
                    Matrix m = new Matrix();
                    if (kind == Transform.MIRROR_H) {
                        m.setScale(-1, 1);
                        m.postTranslate(source.getWidth(), 0);
                    } else if (kind == Transform.MIRROR_V) {
                        m.setScale(1, -1);
                        m.postTranslate(0, source.getHeight());
                    } else {
                        m.postRotate(90);
                    }
                    try {
                        nb = Bitmap.createBitmap(source, 0, 0,
                                source.getWidth(), source.getHeight(), m, true);
                    } catch (Exception ignored) {
                    }
                }
                final Bitmap out = nb;
                final Map<Integer, Integer> remap = remapEdits(kind, oc, orow);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        editMap.clear();
                        editMap.putAll(remap);
                        // 变换后旧快照坐标全部失效,撤销历史作废
                        undoStack.clear();
                        redoStack.clear();
                        updateUndoRedoButtons();
                        if (kind == Transform.ROTATE_90) {
                            int t = cols;
                            cols = rows;
                            rows = t;
                        }
                        if (blankCanvas || out == null) {
                            rebuildBlankRaw();
                        } else {
                            source = out;
                            scheduleRegen();
                        }
                        updateEditsButton();
                    }
                });
            }
        });
    }

    /** 把手动修格覆盖按几何变换映到新坐标;旋转返回的键按新宽度编码 */
    private Map<Integer, Integer> remapEdits(int kind, int w, int h) {
        Map<Integer, Integer> out = new HashMap<>();
        for (Map.Entry<Integer, Integer> e : editMap.entrySet()) {
            int x = e.getKey() % w;
            int y = e.getKey() / w;
            int nx = x, ny = y;
            if (kind == Transform.MIRROR_H) {
                nx = w - 1 - x;
            } else if (kind == Transform.MIRROR_V) {
                ny = h - 1 - y;
            } else {
                nx = h - 1 - y;
                ny = x;
            }
            int newW = kind == Transform.ROTATE_90 ? h : w;
            out.put(ny * newW + nx, e.getValue());
        }
        return out;
    }

    // ---------------- 项目存档(保存 / 打开) ----------------

    private void saveProjectDialog() {
        if (pattern == null) {
            Toast.makeText(this, "图纸还没生成好,稍等一下", Toast.LENGTH_SHORT).show();
            return;
        }
        String def = "拼豆_" + new SimpleDateFormat("MMdd_HHmm", Locale.CHINA)
                .format(new Date());
        final EditText input = new EditText(this);
        input.setText(def);
        input.setSelection(def.length());
        new AlertDialog.Builder(this)
                .setTitle("保存项目存档 💾")
                .setMessage("照片(压缩备份)、全部参数和手动修改会存进 APP,"
                        + "之后可在首页「我的项目」继续编辑或导出。")
                .setView(input)
                .setPositiveButton("保存", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        saveProjectNow(input.getText().toString().trim());
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void saveProjectNow(String name) {
        if (name.isEmpty()) {
            Toast.makeText(this, "名字不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        if (blankCanvas && editMap.isEmpty()) {
            Toast.makeText(this, "画布还是空的,先画点什么吧 ✏️", Toast.LENGTH_SHORT).show();
            return;
        }
        showLoading(true);
        final long savedAt = System.currentTimeMillis();
        exec.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject o = new JSONObject();
                    o.put("name", name);
                    o.put("savedAt", savedAt);
                    o.put("blank", blankCanvas);

                    JSONObject s = new JSONObject();
                    s.put("cols", cols);
                    s.put("rows", rows);
                    s.put("tierIdx", tierIdx);
                    s.put("dither", dither);
                    s.put("brightness", brightness);
                    s.put("contrast", contrast);
                    s.put("saturation", saturation);
                    s.put("style", style);
                    s.put("brickIdx", brickIdx);
                    s.put("absUse", abstractUsePalette);
                    s.put("absColors", abstractColors);
                    s.put("absSnap", abstractSnap);
                    s.put("bgOn", bgRemove);
                    s.put("bgTol", bgTolerance);
                    s.put("round", roundBoard);
                    s.put("limitIdx", maxColorsIdx);
                    s.put("dominant", dominant);
                    s.put("denoise", denoise);
                    s.put("precise", preciseColor);
                    o.put("settings", s);

                    JSONArray ed = new JSONArray();
                    for (Map.Entry<Integer, Integer> en : editMap.entrySet()) {
                        JSONArray pair = new JSONArray();
                        pair.put(en.getKey());
                        pair.put(en.getValue());
                        ed.put(pair);
                    }
                    o.put("edits", ed);
                    if (imported && rawPattern != null) {
                        // 导入图纸没有源照片,把完整格子数据存进项目才能再次打开
                        o.put("share", PatternShare.build(rawPattern, name));
                    }

                    JSONArray bd = new JSONArray();
                    for (int k : beadDone) bd.put(k);
                    o.put("beadDone", bd);
                    o.put("beadDoneDay", beadDoneDay);
                    o.put("beadDoneToday", beadDoneToday);

                    if (!blankCanvas && source != null && !source.isRecycled()) {
                        o.put("photo", Jsons.encodeBitmap(source, 1024, 85));
                        o.put("thumb", Jsons.encodeBitmap(source, 160, 65));
                    }

                    File f = ProjectStore.create(EditorActivity.this, name, savedAt);
                    Jsons.write(f, o);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showLoading(false);
                            Toast.makeText(EditorActivity.this,
                                    "已存档:「" + name + "」,首页「我的项目」可查看",
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showLoading(false);
                            Toast.makeText(EditorActivity.this,
                                    "存档失败:" + e.getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        });
    }

    /** 打开项目存档:还原全部状态后重新生成 */
    private void loadProject(String json) {
        try {
            JSONObject o = new JSONObject(json);
            JSONObject s = o.getJSONObject("settings");

            cols = clampSize(s.optInt("cols", 58));
            rows = clampSize(s.optInt("rows", 58));
            tierIdx = Math.max(0, Math.min(BeadPalettes.selCount() - 1,
                    s.optInt("tierIdx", 2)));
            dither = s.optBoolean("dither", false);
            brightness = clampInt(s.optInt("brightness"), -100, 100);
            contrast = clampInt(s.optInt("contrast"), -100, 100);
            saturation = clampInt(s.optInt("saturation"), -100, 100);
            style = s.optInt("style") == PatternEngine.STYLE_ABSTRACT
                    ? PatternEngine.STYLE_ABSTRACT : PatternEngine.STYLE_REALISTIC;
            brickIdx = Math.max(0, Math.min(3, s.optInt("brickIdx", 1)));
            abstractUsePalette = s.optBoolean("absUse", true);
            abstractColors = Math.max(4, Math.min(16, s.optInt("absColors", 8)));
            abstractSnap = s.optBoolean("absSnap", true);
            bgRemove = s.optBoolean("bgOn", false);
            bgTolerance = s.has("bgTol")
                    ? clampInt(s.optInt("bgTol", 45), 0, 100)
                    : LEGACY_BG_TOL[Math.max(0, Math.min(2, s.optInt("bgIdx", 1)))];
            roundBoard = s.optBoolean("round", false);
            blankCanvas = o.optBoolean("blank", false);
            aiRunning = false;

            editMap.clear();
            JSONArray ed = o.optJSONArray("edits");
            if (ed != null) {
                for (int i = 0; i < ed.length(); i++) {
                    JSONArray pair = ed.optJSONArray(i);
                    if (pair == null || pair.length() < 2) continue;
                    int k = pair.optInt(0, -1);
                    int v = pair.optInt(1, -99);
                    if (k >= 0 && k < cols * rows && v >= -1) {
                        editMap.put(k, v);
                    }
                }
            }

            maxColorsIdx = Math.max(0, Math.min(COLOR_LIMITS.length - 1,
                    s.optInt("limitIdx", 0)));
            dominant = s.optBoolean("dominant", false);
            denoise = Math.max(0, Math.min(3, s.optInt("denoise", 0)));
            preciseColor = s.optBoolean("precise", false);
            JSONObject sh = o.optJSONObject("share");
            if (sh != null) {
                imported = true;
                rawPattern = PatternShare.parse(sh);
            } else {
                imported = false;
            }
            beadDone.clear();
            JSONArray bd = o.optJSONArray("beadDone");
            if (bd != null) {
                for (int i = 0; i < bd.length(); i++) {
                    int k = bd.optInt(i, -1);
                    if (k >= 0 && k < cols * rows) beadDone.add(k);
                }
            }
            beadDoneDay = o.optString("beadDoneDay", "");
            beadDoneToday = Math.max(0, o.optInt("beadDoneToday", 0));
            rollBeadDay();

            String photo = o.optString("photo", "");
            if (!photo.isEmpty()) {
                byte[] raw = android.util.Base64.decode(photo, android.util.Base64.NO_WRAP);
                source = BitmapFactory.decodeByteArray(raw, 0, raw.length);
            } else {
                source = null;
            }
            originalSource = source;

            btnAiRestore.setVisibility(View.GONE);
            syncLoadedWidgets();

            if (imported && rawPattern != null) {
                blankCanvas = true;
                hidePhotoOnlyCards();
                pattern = rawPattern;
                patternView.setPattern(pattern);
                adapter.notifyDataSetChanged();
                updateSummary();
                updateEditsButton();
            } else if (blankCanvas || source == null) {
                blankCanvas = true;
                hidePhotoOnlyCards();
                rebuildBlankRaw();
            } else {
                regenerate();
            }
            Toast.makeText(this, "项目「" + o.optString("name", "") + "」已打开",
                    Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "这个项目文件读不出来了", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private int clampInt(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** 项目加载后把所有控件同步到恢复出来的状态 */
    private void syncLoadedWidgets() {
        // 两个下拉框:延迟复位抑制标志,保证异步回调被吞掉
        suppressSpinner = true;
        paletteSpinner.setSelection(tierIdx, true);
        suppressAbsSpinner = true;
        int absPos = 0;
        for (int i = 0; i < ABSTRACT_CHOICES.length; i++) {
            if (ABSTRACT_CHOICES[i] <= abstractColors) absPos = i;
        }
        abstractColorSpinner.setSelection(absPos, true);
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                suppressSpinner = false;
                suppressAbsSpinner = false;
            }
        }, 80);

        swKmeans.setChecked(abstractUsePalette);
        int vis = abstractUsePalette ? View.VISIBLE : View.GONE;
        colorRow.setVisibility(vis);
        snapRow.setVisibility(vis);
        swSnap.setChecked(abstractSnap);
        swDither.setChecked(dither);
        swDominant.setChecked(dominant);
        swPrecise.setChecked(preciseColor);
        sbDenoise.setProgress(denoise);
        tvDenoise.setText(new String[]{"关", "轻", "中", "强"}[denoise]);
        swSymbols.setChecked(true);
        swGrid.setChecked(true);

        swEditBg.setChecked(bgRemove);
        bgStrengthRow.setVisibility(bgRemove ? View.VISIBLE : View.GONE);
        syncBgUi();

        for (int i = 0; i < chipLimits.length; i++) {
            chipLimits[i].setSelected(i == maxColorsIdx);
        }

        swBricklessSync();
        chipStyleReal.setSelected(style == PatternEngine.STYLE_REALISTIC);
        chipStyleAbs.setSelected(style == PatternEngine.STYLE_ABSTRACT);
        abstractPanel.setVisibility(
                style == PatternEngine.STYLE_ABSTRACT ? View.VISIBLE : View.GONE);

        editCell = false;
        swEditCell.setOnCheckedChangeListener(null);
        swEditCell.setChecked(false);
        swEditCell.setOnCheckedChangeListener(new Switch.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                editCell = isChecked;
                if (isChecked && paintMode) {
                    setPaintMode(false, true);
                }
                Toast.makeText(EditorActivity.this,
                        isChecked ? "已开启:去「图纸」页点任意格子换色"
                                : "已关闭点格修改",
                        Toast.LENGTH_SHORT).show();
            }
        });
        setPaintMode(false, true);

        setSeek(R.id.sbBright, brightness + 100);
        setSeek(R.id.sbContrast, contrast + 100);
        setSeek(R.id.sbSat, saturation + 100);

        if (blankCanvas) {
            hidePhotoOnlyCards();
        }
        customSizeRow.setVisibility(roundBoard ? View.GONE : View.VISIBLE);
        undoStack.clear();
        redoStack.clear();
        updateUndoRedoButtons();
        syncSizeUi();
    }

    /** 小工具:同步砖块大小四颗 chip 的选中态(不触发重算) */
    private void swBricklessSync() {
        syncBrickUi();
    }



    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_STORAGE) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED
                && pendingExport > 0) {
            doExport(pendingExport);
        } else if (pendingExport > 0) {
            Toast.makeText(this, "没有存储权限,无法保存到相册", Toast.LENGTH_SHORT).show();
        }
        pendingExport = 0;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        main.removeCallbacks(regenTask);
        exec.shutdownNow();
    }
}
