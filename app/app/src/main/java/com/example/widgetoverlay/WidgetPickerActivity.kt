package com.example.widgetoverlay

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.widgetoverlay.ui.Design
import com.example.widgetoverlay.ui.applySystemBarsInsets
import com.example.widgetoverlay.ui.dp
import com.example.widgetoverlay.ui.roundedRect
import com.example.widgetoverlay.ui.themeColor
import com.example.widgetoverlay.ui.tonalIconButton
import com.google.android.material.R as MaterialR
import com.google.android.material.color.DynamicColors
import kotlin.math.roundToInt

class WidgetPickerActivity : AppCompatActivity() {
    private lateinit var appWidgetManager: AppWidgetManager
    private lateinit var widgetHost: android.appwidget.AppWidgetHost
    private lateinit var recyclerView: RecyclerView
    private lateinit var searchInput: EditText
    private lateinit var emptyView: TextView

    private var allGroups: List<WidgetGroup> = emptyList()
    private var filteredGroups: List<WidgetGroup> = emptyList()
    private var pendingWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DynamicColors.applyToActivityIfAvailable(this)
        appWidgetManager = AppWidgetManager.getInstance(this)
        widgetHost = android.appwidget.AppWidgetHost(this, HOST_ID)

        // Restore pendingWidgetId if activity was recreated
        if (savedInstanceState != null) {
            pendingWidgetId = savedInstanceState.getInt(
                KEY_PENDING_WIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

        // Allocate new ID only if we don't have one
        if (pendingWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            pendingWidgetId = widgetHost.allocateAppWidgetId()
        }

        setContentView(createContentView())
        loadWidgets()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_PENDING_WIDGET_ID, pendingWidgetId)
    }

    override fun onDestroy() {
        // Only delete if we're finishing, not if being recreated
        if (isFinishing) {
            deleteWidgetIdSafely(pendingWidgetId)
        }
        super.onDestroy()
    }

    private fun createContentView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(themeColor(MaterialR.attr.colorSurface))
        }
        applySystemBarsInsets(root)

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(12), dp(4))
        }
        header.addView(TextView(this).apply {
            text = getString(R.string.widget_picker_title)
            setTextColor(themeColor(MaterialR.attr.colorOnSurface))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(
            tonalIconButton(R.drawable.ic_close, getString(R.string.close)) { finish() },
            LinearLayout.LayoutParams(dp(40), dp(40)),
        )
        root.addView(header, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))

        // Search bar
        val search = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedRect(
                themeColor(MaterialR.attr.colorSurfaceContainerHigh),
                Design.RADIUS_XLARGE,
            )
            setPadding(dp(16), 0, dp(16), 0)
        }
        search.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_search)
            imageTintList = ColorStateList.valueOf(
                themeColor(MaterialR.attr.colorOnSurfaceVariant)
            )
        }, LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(12) })
        searchInput = EditText(this).apply {
            hint = getString(R.string.widget_picker_search)
            setTextColor(themeColor(MaterialR.attr.colorOnSurface))
            setHintTextColor(themeColor(MaterialR.attr.colorOnSurfaceVariant))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            background = null
            setPadding(0, dp(14), 0, dp(14))
            imeOptions = EditorInfo.IME_ACTION_DONE
            setSingleLine(true)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    filterWidgets(s?.toString() ?: "")
                }
            })
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    hideKeyboard()
                    true
                } else false
            }
        }
        search.addView(searchInput, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ))
        root.addView(search, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(dp(16), dp(12), dp(16), dp(12)) })

        // Grid
        recyclerView = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@WidgetPickerActivity, GRID_SPAN_COUNT).apply {
                spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int =
                        if (recyclerView.adapter?.getItemViewType(position) == VIEW_TYPE_HEADER)
                            GRID_SPAN_COUNT else 1
                }
            }
            clipToPadding = false
            setPadding(dp(8), 0, dp(8), dp(16))
        }
        root.addView(recyclerView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0, 1f,
        ))

        // Empty view
        emptyView = TextView(this).apply {
            text = getString(R.string.widget_picker_empty)
            setTextColor(themeColor(MaterialR.attr.colorOnSurfaceVariant))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(dp(32), dp(48), dp(32), dp(48))
        }
        root.addView(emptyView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))

        return root
    }

    private fun loadWidgets() {
        val pm = packageManager

        // Get all installed widget providers
        val providers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appWidgetManager.getInstalledProvidersForProfile(Process.myUserHandle())
        } else {
            @Suppress("DEPRECATION")
            appWidgetManager.installedProviders
        }

        // Group by app and build display list
        val groups = providers
            .groupBy { it.provider.packageName }
            .map { (packageName, widgetProviders) ->
                val appLabel = try {
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (_: PackageManager.NameNotFoundException) {
                    packageName
                }
                val appIcon = try {
                    pm.getApplicationIcon(packageName)
                } catch (_: PackageManager.NameNotFoundException) {
                    null
                }
                val widgets = widgetProviders.map { info ->
                    val minWidth = info.minWidth
                    val minHeight = info.minHeight

                    WidgetItem(
                        info = info,
                        label = info.loadLabel(pm) ?: info.provider.className.substringAfterLast('.'),
                        previewImage = loadPreviewSafely(info, pm),
                        appIcon = appIcon,
                        minWidthDp = minWidth,
                        minHeightDp = minHeight,
                        minSizeCells = widgetCells(info),
                    )
                }.sortedBy { it.label }
                WidgetGroup(
                    packageName = packageName,
                    appLabel = appLabel,
                    appIcon = appIcon,
                    widgets = widgets,
                )
            }
            .sortedBy { it.appLabel.lowercase() }

        allGroups = groups
        filteredGroups = groups
        recyclerView.adapter = WidgetAdapter()
    }

    private fun loadPreviewSafely(info: AppWidgetProviderInfo, pm: PackageManager): Drawable? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                info.loadPreviewImage(this, resources.displayMetrics.densityDpi)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun filterWidgets(query: String) {
        val trimmed = query.trim().lowercase()
        filteredGroups = if (trimmed.isEmpty()) {
            allGroups
        } else {
            allGroups.mapNotNull { group ->
                val matchedWidgets = group.widgets.filter {
                    it.label.lowercase().contains(trimmed) ||
                        group.appLabel.lowercase().contains(trimmed)
                }
                if (matchedWidgets.isEmpty()) null
                else group.copy(widgets = matchedWidgets)
            }
        }
        recyclerView.adapter = WidgetAdapter()
        emptyView.visibility = if (filteredGroups.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun onWidgetSelected(info: AppWidgetProviderInfo) {
        val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
        }
        startActivityForResult(bindIntent, REQUEST_BIND)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_BIND -> {
                if (resultCode == RESULT_OK && data != null) {
                    val id = data.getIntExtra(
                        AppWidgetManager.EXTRA_APPWIDGET_ID,
                        pendingWidgetId,
                    )
                    pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

                    if (id == AppWidgetManager.INVALID_APPWIDGET_ID) {
                        finish()
                        return
                    }

                    val info = try {
                        appWidgetManager.getAppWidgetInfo(id)
                    } catch (_: Exception) {
                        null
                    }

                    if (info == null) {
                        deleteWidgetIdSafely(id)
                        finish()
                        return
                    }

                    if (info.configure != null) {
                        startConfigureActivity(id, info)
                    } else {
                        commitResult(id)
                    }
                } else {
                    // Binding denied or cancelled
                    pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
                    finish()
                }
            }
            REQUEST_CONFIGURE -> {
                if (resultCode == RESULT_OK) {
                    commitResult(pendingWidgetId)
                } else {
                    deleteWidgetIdSafely(pendingWidgetId)
                    finish()
                }
                pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
            }
        }
    }

    private fun startConfigureActivity(id: Int, info: AppWidgetProviderInfo) {
        val configureComponent = info.configure
        if (configureComponent == null) {
            commitResult(id)
            return
        }

        pendingWidgetId = id
        try {
            // The configure activity must launch in this task: adding FLAG_ACTIVITY_NEW_TASK
            // would make startActivityForResult() deliver RESULT_CANCELED immediately,
            // deleting the widget id before the user finishes configuring it.
            val configureIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = configureComponent
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            }
            startActivityForResult(configureIntent, REQUEST_CONFIGURE)
        } catch (_: Exception) {
            // Configure activity not found or cannot be started
            pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
            commitResult(id)
        }
    }

    private fun commitResult(widgetId: Int) {
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setResult(RESULT_OK, Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        })
        finish()
    }

    private fun deleteWidgetIdSafely(id: Int) {
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
        try {
            widgetHost.deleteAppWidgetId(id)
        } catch (_: Exception) {
            // Ignore errors when deleting widget ID
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchInput.windowToken, 0)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    /**
     * Convert dp dimensions to grid cells.
     * Based on AOSP Launcher3 standard cell sizes:
     * - 4 columns on phones
     * - Cell size varies by device, but standard is approximately:
     *   - 1x1: 40dp (min), typically 60-80dp
     *   - 2x2: 110dp (min)
     *   - 4x2: 250dp (min)
     *   - 4x4: 250dp (min)
     *
     * The AOSP launcher formula: cells = ceil(minSize / cellSize)
     * where cellSize = (availableWidth - (numCols-1) * gap) / numCols
     */
    /**
     * Widget size in home-grid cells. Prefers the provider's declared target cells (API 31+,
     * exactly what launchers use); the dp fallback is clamped to a realistic phone grid
     * (max 5 columns x 6 rows) so wide minimum widths never display as 7+ cells.
     */
    private fun widgetCells(info: AppWidgetProviderInfo): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            (info.targetCellWidth > 0 || info.targetCellHeight > 0)
        ) {
            return info.targetCellWidth.coerceIn(1, MAX_GRID_COLUMNS) to
                info.targetCellHeight.coerceIn(1, MAX_GRID_ROWS)
        }
        val (cols, rows) = dpToCells(info.minWidth, info.minHeight)
        return cols.coerceIn(1, MAX_GRID_COLUMNS) to rows.coerceIn(1, MAX_GRID_ROWS)
    }

    private fun dpToCells(widthDp: Int, heightDp: Int): Pair<Int, Int> {
        // AOSP Launcher3 standard: 4 columns, cell size calculated from screen
        val displayMetrics = resources.displayMetrics
        val screenWidthPx = displayMetrics.widthPixels
        val density = displayMetrics.density

        // Standard AOSP launcher uses 4 columns
        val numColumns = 4
        // Typical launcher padding: 16dp on each side
        val launcherPaddingPx = (16 * density).toInt()
        // Cell gap: typically 0-2dp
        val cellGapPx = (0 * density).toInt()

        // Calculate cell width based on AOSP formula
        val availableWidthPx = screenWidthPx - (2 * launcherPaddingPx)
        val cellWidthPx = (availableWidthPx - ((numColumns - 1) * cellGapPx)) / numColumns
        val cellWidthDp = (cellWidthPx / density).roundToInt()

        // Cell height is typically the same as cell width on most launchers
        val cellHeightDp = cellWidthDp

        // Convert minSize to cells using AOSP formula:
        // cells = ceil(minSize / cellSize)
        val cellsX = maxOf(1, ((widthDp + cellWidthDp - 1) / cellWidthDp))
        val cellsY = maxOf(1, ((heightDp + cellHeightDp - 1) / cellHeightDp))

        return Pair(cellsX, cellsY)
    }

    private fun formatCellSize(cells: Pair<Int, Int>): String {
        return "${cells.first}×${cells.second}"
    }

    // Data classes
    data class WidgetGroup(
        val packageName: String,
        val appLabel: String,
        val appIcon: Drawable?,
        val widgets: List<WidgetItem>,
    )

    data class WidgetItem(
        val info: AppWidgetProviderInfo,
        val label: String,
        val previewImage: Drawable?,
        val appIcon: Drawable?,
        val minWidthDp: Int,
        val minHeightDp: Int,
        val minSizeCells: Pair<Int, Int>,
    )

    // RecyclerView Adapter with app-group headers and a 2-column widget grid
    private inner class WidgetAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private val flattenedItems: List<Any> = buildList {
            for (group in filteredGroups) {
                add(group)
                addAll(group.widgets)
            }
        }

        override fun getItemViewType(position: Int): Int {
            return if (flattenedItems[position] is WidgetGroup) VIEW_TYPE_HEADER else VIEW_TYPE_WIDGET
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == VIEW_TYPE_HEADER) {
                HeaderViewHolder(createHeaderView())
            } else {
                WidgetViewHolder(createWidgetCell(parent))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (holder) {
                is HeaderViewHolder -> holder.bind(flattenedItems[position] as WidgetGroup)
                is WidgetViewHolder -> holder.bind(flattenedItems[position] as WidgetItem)
            }
        }

        override fun getItemCount(): Int = flattenedItems.size
    }

    // Header ViewHolder (app group)
    private inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val iconView: ImageView = view.findViewById(VIEW_ID_ICON)
        private val labelView: TextView = view.findViewById(VIEW_ID_LABEL)
        private val countView: TextView = view.findViewById(VIEW_ID_COUNT)

        fun bind(group: WidgetGroup) {
            iconView.setImageDrawable(group.appIcon)
            labelView.text = group.appLabel
            countView.text = getString(R.string.widget_picker_count, group.widgets.size)
        }
    }

    // Widget cell ViewHolder
    private inner class WidgetViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val previewView: ImageView = view.findViewById(VIEW_ID_PREVIEW)
        private val nameView: TextView = view.findViewById(VIEW_ID_NAME)
        private val sizeView: TextView = view.findViewById(VIEW_ID_SIZE)

        fun bind(item: WidgetItem) {
            nameView.text = item.label
            sizeView.text = "${formatCellSize(item.minSizeCells)} (${item.minWidthDp}×${item.minHeightDp}dp)"

            val preview = item.previewImage ?: item.appIcon
            if (preview != null) {
                previewView.setImageDrawable(preview)
                previewView.imageTintList = null
            } else {
                previewView.setImageResource(R.drawable.ic_apps)
                previewView.imageTintList = ColorStateList.valueOf(
                    themeColor(MaterialR.attr.colorOnSurfaceVariant)
                )
            }

            itemView.setOnClickListener { onWidgetSelected(item.info) }
        }
    }

    private fun createHeaderView(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(16), dp(12), dp(8))
    }.apply {
        addView(ImageView(this@WidgetPickerActivity).apply {
            id = VIEW_ID_ICON
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(0, 0, dp(10), 0)
        }, LinearLayout.LayoutParams(dp(24), dp(24)))
        addView(TextView(this@WidgetPickerActivity).apply {
            id = VIEW_ID_LABEL
            setTextColor(themeColor(MaterialR.attr.colorOnSurface))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(this@WidgetPickerActivity).apply {
            id = VIEW_ID_COUNT
            setTextColor(themeColor(MaterialR.attr.colorOnSurfaceVariant))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
    }

    private fun createWidgetCell(parent: ViewGroup): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(8), dp(8), dp(8), dp(8))
        isClickable = true
        isFocusable = true
        val outValue = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
        foreground = androidx.core.content.ContextCompat.getDrawable(
            this@WidgetPickerActivity, outValue.resourceId
        )

        // Square preview well sized to the grid span
        val cellSize = (parent.width / GRID_SPAN_COUNT - dp(16)).coerceAtLeast(dp(96))
        addView(FrameLayout(this@WidgetPickerActivity).apply {
            id = VIEW_ID_PREVIEW_CONTAINER
            background = roundedRect(
                themeColor(MaterialR.attr.colorSurfaceContainerLow),
                Design.RADIUS_MEDIUM,
            )
            setPadding(dp(12), dp(12), dp(12), dp(12))
            addView(ImageView(this@WidgetPickerActivity).apply {
                id = VIEW_ID_PREVIEW
                scaleType = ImageView.ScaleType.FIT_CENTER
            }, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, cellSize))

        addView(TextView(this@WidgetPickerActivity).apply {
            id = VIEW_ID_NAME
            setTextColor(themeColor(MaterialR.attr.colorOnSurface))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(8), 0, 0)
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        addView(TextView(this@WidgetPickerActivity).apply {
            id = VIEW_ID_SIZE
            setTextColor(themeColor(MaterialR.attr.colorOnSurfaceVariant))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
    }

    companion object {
        private const val HOST_ID = 0x574F564C // WOVL - same host ID
        private const val REQUEST_BIND = 2001
        private const val REQUEST_CONFIGURE = 2002
        private const val KEY_PENDING_WIDGET_ID = "pending_widget_id"

        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_WIDGET = 1
        private const val GRID_SPAN_COUNT = 2
        private const val MAX_GRID_COLUMNS = 5
        private const val MAX_GRID_ROWS = 6

        private const val VIEW_ID_ICON = 1
        private const val VIEW_ID_LABEL = 2
        private const val VIEW_ID_COUNT = 3
        private const val VIEW_ID_PREVIEW = 4
        private const val VIEW_ID_NAME = 5
        private const val VIEW_ID_SIZE = 6
        private const val VIEW_ID_PREVIEW_CONTAINER = 7
    }
}
