/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.android.accessibility.talkback.compositor;

import static android.accessibilityservice.MagnificationConfig.MAGNIFICATION_MODE_FULLSCREEN;
import static android.accessibilityservice.MagnificationConfig.MAGNIFICATION_MODE_WINDOW;
import static com.google.android.accessibility.talkback.compositor.Compositor.DESC_ORDER_ROLE_NAME_STATE_POSITION;
import static com.google.android.accessibility.talkback.compositor.Compositor.ENUM_VERBOSITY_DESCRIPTION_ORDER;
import static com.google.android.accessibility.talkback.eventprocessor.ProcessorMagnification.STATE_OFF;
import static com.google.android.accessibility.utils.input.InputModeManager.INPUT_MODE_KEYBOARD;
import static com.google.android.accessibility.utils.input.InputModeManager.INPUT_MODE_NON_ALPHABETIC_KEYBOARD;
import static com.google.android.accessibility.utils.input.InputModeManager.INPUT_MODE_TOUCH;
import static com.google.android.accessibility.utils.input.InputModeManager.INPUT_MODE_TV_REMOTE;
import static com.google.android.accessibility.utils.input.InputModeManager.INPUT_MODE_UNKNOWN;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.compositor.Compositor.DescriptionOrder;
import com.google.android.accessibility.talkback.compositor.parsetree.ParseTree;
import com.google.android.accessibility.talkback.compositor.parsetree.ParseTree.VariableDelegate;
import com.google.android.accessibility.talkback.compositor.parsetree.ParseTreeJoinNode;
import com.google.android.accessibility.talkback.eventprocessor.ProcessorMagnification;
import com.google.android.accessibility.talkback.eventprocessor.ProcessorMagnification.MagnificationState;
import com.google.android.accessibility.talkback.keyboard.KeyComboManager;
import com.google.android.accessibility.talkback.keyboard.KeyComboModel;
import com.google.android.accessibility.utils.AccessibilityEventUtils;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.CollectionState;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.KeyboardUtils;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.SpannableUtils;
import com.google.android.accessibility.utils.TimedFlags;
import com.google.android.accessibility.utils.compat.provider.SettingsCompatUtils;
import com.google.android.accessibility.utils.input.InputModeManager;
import com.google.android.accessibility.utils.input.WindowsDelegate;
import com.google.android.accessibility.utils.output.SpeechCleanupUtils;
import com.google.android.apps.common.proguard.UsedByReflection;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import com.google.common.base.Ascii;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Tracks the current global state for the parse tree. */
public class GlobalVariables extends TimedFlags implements ParseTree.VariableDelegate {
  private static final String TAG = "GlobalVariables";
  // Parameters used in join statement.
  private static final CharSequence SEPARATOR = ", ";
  private static final CharSequence EMPTY_STRING = "";
  private static final boolean PRUNE_EMPTY = true;

  public static final int EVENT_SKIP_FOCUS_PROCESSING_AFTER_GRANULARITY_MOVE = 1;
  public static final int EVENT_SKIP_FOCUS_PROCESSING_AFTER_CURSOR_CONTROL = 3;

  /**
   * Indicates that the system has moved the cursor after an edit field was focused, and we want to
   * avoid providing feedback because we're about to reset the cursor.
   */
  public static final int EVENT_SKIP_SELECTION_CHANGED_AFTER_FOCUSED = 9;

  /** Indicates that we've automatically snapped text selection state and don't want feedback. */
  public static final int EVENT_SKIP_SELECTION_CHANGED_AFTER_CURSOR_RESET = 10;

  /** Used to suppress focus announcement when refocusing after an IME has closed. */
  public static final int EVENT_SKIP_FOCUS_PROCESSING_AFTER_IME_CLOSED = 13;

  /**
   * Indicates that the next ACCESSIBILITY_FOCUSED event is triggered by the synchronization of a11y
   * focus and input focus. The generated utterance should not have FLAG_FORCE_FEEDBACK flag,
   * because it's not generated by touch exploration.
   */
  public static final int EVENT_SYNCED_ACCESSIBILITY_FOCUS = 14;

  // Parse tree constants.
  private static final int ENUM_COLLECTION_HEADING_TYPE = 6000;
  private static final int ENUM_INPUT_MODE = 6002;

  private static final int GLOBAL_SYNCED_ACCESSIBILITY_FOCUS_LATCH = 6000;
  private static final int GLOBAL_IS_KEYBOARD_ACTIVE = 6001;
  private static final int GLOBAL_IS_SELECTION_MODE_ACTIVE = 6002;
  private static final int GLOBAL_INPUT_MODE = 6003;
  private static final int GLOBAL_USE_SINGLE_TAP = 6004;
  private static final int GLOBAL_SPEECH_RATE_CHANGE = 6005;
  private static final int GLOBAL_USE_AUDIO_FOCUS = 6007;
  private static final int GLOBAL_LAST_TEXT_EDIT_IS_PASSWORD = 6008;
  private static final int GLOBAL_SPEAK_PASS_SERVICE_POLICY = 6009;
  private static final int GLOBAL_SPEAK_PASS_FIELD_CONTENT = 6010;
  private static final int GLOBAL_ENABLE_USAGE_HINT = 6011;
  private static final int GLOBAL_SEEKBAR_HINT = 6012;
  private static final int GLOBAL_INTERPRET_AS_ENTRY_KEY = 6013;

  private static final int COLLECTION_NAME = 6100;
  private static final int COLLECTION_ROLE = 6101;
  private static final int COLLECTION_TRANSITION = 6102;
  private static final int COLLECTION_EXISTS = 6103;
  private static final int COLLECTION_IS_ROW_TRANSITION = 6104;
  private static final int COLLECTION_IS_COLUMN_TRANSITION = 6105;
  private static final int COLLECTION_TABLE_ITEM_HEADING_TYPE = 6106;
  private static final int COLLECTION_TABLE_ITEM_ROW_NAME = 6107;
  private static final int COLLECTION_TABLE_ITEM_ROW_INDEX = 6108;
  private static final int COLLECTION_TABLE_ITEM_COLUMN_NAME = 6109;
  private static final int COLLECTION_TABLE_ITEM_COLUMN_INDEX = 6110;
  private static final int COLLECTION_LIST_ITEM_IS_HEADING = 6111;
  private static final int COLLECTION_PAGER_ITEM_ROW_INDEX = 6112;
  private static final int COLLECTION_PAGER_ITEM_COLUMN_INDEX = 6113;
  private static final int COLLECTION_PAGER_ITEM_IS_HEADING = 6114;
  private static final int COLLECTION_LIST_ITEM_POSITION_DESCRIPTION = 6115;

  private static final int WINDOWS_LAST_WINDOW_ID = 6200;
  private static final int WINDOWS_IS_SPLIT_SCREEN_MODE = 6201;

  private static final int FOCUS_IS_CURRENT_FOCUS_IN_SCROLLABLE_NODE = 6300;
  private static final int FOCUS_IS_LAST_FOCUS_IN_SCROLLABLE_NODE = 6301;

  private static final int KEY_COMBO_HAS_KEY_FOR_CLICK = 6400;
  private static final int KEY_COMBO_STRING_FOR_CLICK = 6401;
  private static final int KEY_COMBO_HAS_KEY_FOR_LONG_CLICK = 6402;
  private static final int KEY_COMBO_STRING_FOR_LONG_CLICK = 6403;

  private static final int MAGNIFICATION_STATE_CHANGED = 6500;

  private static final int GESTURE_STRING_FOR_NODE_ACTIONS = 6600;

  // Verbosity
  private static final int VERBOSITY_SPEAK_ROLES = 10001;
  private static final int VERBOSITY_SPEAK_COLLECTION_INFO = 10002;
  private static final int VERBOSITY_DESCRIPTION_ORDER = 10003;
  private static final int VERBOSITY_SPEAK_ELEMENT_IDS = 10004;
  private static final int VERBOSITY_SPEAK_SYSTEM_WINDOW_TITLES = 10005;

  private final Context mContext;
  private final AccessibilityService mService;
  private final InputModeManager mInputModeManager;
  private final @Nullable KeyComboManager mKeyComboManager;
  private final CollectionState mCollectionState = new CollectionState();
  private WindowsDelegate mWindowsDelegate;

  private boolean mUseSingleTap = false;
  private float mSpeechRate = 1.0f;
  private boolean mUseAudioFocus = false;

  private int mLastWindowId = -1;
  private int mCurrentWindowId = -1;
  private int currentDisplayId = Display.INVALID_DISPLAY;

  private Integer magnificationMode = null;
  private float magnificationCurrentScale = -1.0f;
  private @MagnificationState int magnificationState = STATE_OFF;

  private boolean mSelectionModeActive;
  private boolean mLastTextEditIsPassword;

  private boolean mIsCurrentFocusInScrollableNode = false;
  private boolean mIsLastFocusInScrollableNode = false;
  private boolean isInterpretAsEntryKey = false;

  // Defaults to true so that upgrading to this version will not impact previous behavior.
  private boolean mShouldSpeakPasswords = true;

  private final @Nullable GestureShortcutProvider gestureShortcutProvider;

  // Defaults to true to speak usage hint.
  private boolean usageHintEnabled = true;
  // It's enabled when [Say capital] is configured.
  private boolean sayCapital = false;

  // Verbosity settings
  private boolean speakRoles = true;
  private boolean speakCollectionInfo = true;
  @DescriptionOrder private int descriptionOrder = DESC_ORDER_ROLE_NAME_STATE_POSITION;
  private boolean speakElementIds = false;
  private boolean speakSystemWindowTitles = true;

  public GlobalVariables(
      AccessibilityService service,
      InputModeManager inputModeManager,
      @Nullable KeyComboManager keyComboManager) {
    this(service, inputModeManager, keyComboManager, /* gestureShortcutProvider= */ null);
  }

  public GlobalVariables(
      AccessibilityService service,
      InputModeManager inputModeManager,
      @Nullable KeyComboManager keyComboManager,
      @Nullable GestureShortcutProvider gestureShortcutProvider) {
    mContext = service;
    mService = service;
    mInputModeManager = inputModeManager;
    mKeyComboManager = keyComboManager;
    this.gestureShortcutProvider = gestureShortcutProvider;
  }

  public void setWindowsDelegate(WindowsDelegate delegate) {
    mWindowsDelegate = delegate;
  }

  void declareVariables(ParseTree parseTree) {
    Map<Integer, String> collectionHeadingType = new HashMap<>();
    collectionHeadingType.put(CollectionState.TYPE_NONE, "none");
    collectionHeadingType.put(CollectionState.TYPE_ROW, "row");
    collectionHeadingType.put(CollectionState.TYPE_COLUMN, "column");
    collectionHeadingType.put(CollectionState.TYPE_INDETERMINATE, "indeterminate");

    Map<Integer, String> inputMode = new HashMap<>();
    inputMode.put(INPUT_MODE_UNKNOWN, "unknown");
    inputMode.put(INPUT_MODE_TOUCH, "touch");
    inputMode.put(INPUT_MODE_KEYBOARD, "keyboard");
    inputMode.put(INPUT_MODE_TV_REMOTE, "tv_remote");
    inputMode.put(INPUT_MODE_NON_ALPHABETIC_KEYBOARD, "non_alphabetic_keyboard");

    parseTree.addEnum(ENUM_COLLECTION_HEADING_TYPE, collectionHeadingType);
    parseTree.addEnum(ENUM_INPUT_MODE, inputMode);

    // Globals
    parseTree.addBooleanVariable(
        "global.syncedAccessibilityFocusLatch", GLOBAL_SYNCED_ACCESSIBILITY_FOCUS_LATCH);
    parseTree.addBooleanVariable("global.isKeyboardActive", GLOBAL_IS_KEYBOARD_ACTIVE);
    parseTree.addBooleanVariable("global.isSelectionModeActive", GLOBAL_IS_SELECTION_MODE_ACTIVE);
    parseTree.addEnumVariable("global.inputMode", GLOBAL_INPUT_MODE, ENUM_INPUT_MODE);
    parseTree.addBooleanVariable("global.useSingleTap", GLOBAL_USE_SINGLE_TAP);
    parseTree.addNumberVariable("global.speechRate", GLOBAL_SPEECH_RATE_CHANGE);
    parseTree.addBooleanVariable("global.useAudioFocus", GLOBAL_USE_AUDIO_FOCUS);
    parseTree.addBooleanVariable(
        "global.lastTextEditIsPassword", GLOBAL_LAST_TEXT_EDIT_IS_PASSWORD);
    parseTree.addBooleanVariable(
        "global.speakPasswordsServicePolicy", GLOBAL_SPEAK_PASS_SERVICE_POLICY);
    parseTree.addBooleanVariable(
        "global.speakPasswordFieldContent", GLOBAL_SPEAK_PASS_FIELD_CONTENT);
    parseTree.addBooleanVariable("global.enableUsageHint", GLOBAL_ENABLE_USAGE_HINT);
    parseTree.addStringVariable("global.seekbarHint", GLOBAL_SEEKBAR_HINT);
    parseTree.addBooleanVariable("global.isInterpretAsEntryKey", GLOBAL_INTERPRET_AS_ENTRY_KEY);

    // Collection
    parseTree.addStringVariable("collection.name", COLLECTION_NAME);
    parseTree.addStringVariable("collection.transition", COLLECTION_TRANSITION);
    parseTree.addEnumVariable("collection.role", COLLECTION_ROLE, Compositor.ENUM_ROLE);
    parseTree.addBooleanVariable("collection.exists", COLLECTION_EXISTS);
    parseTree.addBooleanVariable("collection.isRowTransition", COLLECTION_IS_ROW_TRANSITION);
    parseTree.addBooleanVariable("collection.isColumnTransition", COLLECTION_IS_COLUMN_TRANSITION);
    parseTree.addEnumVariable(
        "collection.tableItem.headingType",
        COLLECTION_TABLE_ITEM_HEADING_TYPE,
        ENUM_COLLECTION_HEADING_TYPE);
    parseTree.addStringVariable("collection.tableItem.rowName", COLLECTION_TABLE_ITEM_ROW_NAME);
    parseTree.addIntegerVariable("collection.tableItem.rowIndex", COLLECTION_TABLE_ITEM_ROW_INDEX);
    parseTree.addStringVariable(
        "collection.tableItem.columnName", COLLECTION_TABLE_ITEM_COLUMN_NAME);
    parseTree.addIntegerVariable(
        "collection.tableItem.columnIndex", COLLECTION_TABLE_ITEM_COLUMN_INDEX);
    parseTree.addIntegerVariable("collection.pagerItem.rowIndex", COLLECTION_PAGER_ITEM_ROW_INDEX);
    parseTree.addIntegerVariable(
        "collection.pagerItem.columnIndex", COLLECTION_PAGER_ITEM_COLUMN_INDEX);
    parseTree.addBooleanVariable(
        "collection.pagerItem.isHeading", COLLECTION_PAGER_ITEM_IS_HEADING);
    parseTree.addBooleanVariable("collection.listItem.isHeading", COLLECTION_LIST_ITEM_IS_HEADING);
    parseTree.addStringVariable(
        "collection.listItem.positionDescription", COLLECTION_LIST_ITEM_POSITION_DESCRIPTION);

    parseTree.addBooleanVariable("windows.isSplitScreenMode", WINDOWS_IS_SPLIT_SCREEN_MODE);
    parseTree.addIntegerVariable("windows.lastWindowId", WINDOWS_LAST_WINDOW_ID);

    parseTree.addBooleanVariable(
        "focus.isCurrentFocusInScrollableNode", FOCUS_IS_CURRENT_FOCUS_IN_SCROLLABLE_NODE);
    parseTree.addBooleanVariable(
        "focus.isLastFocusInScrollableNode", FOCUS_IS_LAST_FOCUS_IN_SCROLLABLE_NODE);

    parseTree.addBooleanVariable("keyCombo.hasKeyForClick", KEY_COMBO_HAS_KEY_FOR_CLICK);
    parseTree.addStringVariable(
        "keyCombo.stringRepresentationForClick", KEY_COMBO_STRING_FOR_CLICK);
    parseTree.addBooleanVariable("keyCombo.hasKeyForLongClick", KEY_COMBO_HAS_KEY_FOR_LONG_CLICK);
    parseTree.addStringVariable(
        "keyCombo.stringRepresentationForLongClick", KEY_COMBO_STRING_FOR_LONG_CLICK);

    parseTree.addStringVariable("magnification.stateChanged", MAGNIFICATION_STATE_CHANGED);

    parseTree.addStringVariable("gesture.nodeMenuShortcut", GESTURE_STRING_FOR_NODE_ACTIONS);

    // Verbosity
    parseTree.addBooleanVariable("verbosity.speakRole", VERBOSITY_SPEAK_ROLES);
    parseTree.addBooleanVariable("verbosity.speakCollectionInfo", VERBOSITY_SPEAK_COLLECTION_INFO);
    parseTree.addBooleanVariable(
        "verbosity.speakSystemWindowTitles", VERBOSITY_SPEAK_SYSTEM_WINDOW_TITLES);
    parseTree.addEnumVariable(
        "verbosity.descriptionOrder",
        VERBOSITY_DESCRIPTION_ORDER,
        ENUM_VERBOSITY_DESCRIPTION_ORDER);
    parseTree.addBooleanVariable("verbosity.speakElementIds", VERBOSITY_SPEAK_ELEMENT_IDS);

    // Functions
    parseTree.addFunction("cleanUp", this);
    parseTree.addFunction("collapseRepeatedCharactersAndCleanUp", this);
    parseTree.addFunction("conditionalPrepend", this);
    parseTree.addFunction("conditionalAppend", this);
    parseTree.addFunction("conditionalPrependWithSpaceSeparator", this);
    parseTree.addFunction("getWindowTitle", this);
    parseTree.addFunction("round", this);
    parseTree.addFunction("roundForProgressPercent", this);
    parseTree.addFunction("roundForProgressInt", this);
    parseTree.addFunction("spelling", this);
    parseTree.addFunction("equals", this);
    parseTree.addFunction("dedupJoin", this);
    parseTree.addFunction("prependCapital", this);
  }

  public void updateStateFromEvent(AccessibilityEvent event) {
    int eventType = event.getEventType();
    if (eventType == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
      final AccessibilityNodeInfoCompat sourceNode =
          AccessibilityNodeInfoUtils.toCompat(event.getSource());
      // Transition the collection state if necessary.
      mCollectionState.updateCollectionInformation(sourceNode, event);
      if (sourceNode != null) {
        final AccessibilityNodeInfoCompat scrollableNode =
            AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(
                sourceNode, AccessibilityNodeInfoUtils.FILTER_SCROLLABLE);
        mIsLastFocusInScrollableNode = mIsCurrentFocusInScrollableNode;
        mIsCurrentFocusInScrollableNode = (scrollableNode != null);

        mLastWindowId = mCurrentWindowId;
        mCurrentWindowId = sourceNode.getWindowId();
        currentDisplayId = AccessibilityEventUtils.getDisplayId(event);
      }
    }
  }

  public void setUsageHintEnabled(boolean enabled) {
    usageHintEnabled = enabled;
  }

  public void setGlobalSayCapital(boolean enabled) {
    sayCapital = enabled;
  }

  public void setUseSingleTap(boolean value) {
    mUseSingleTap = value;
  }

  public void setSpeechRate(float value) {
    mSpeechRate = value;
  }

  public void setUseAudioFocus(boolean value) {
    mUseAudioFocus = value;
  }

  public void updateMagnificationState(
      @Nullable Integer mode, float currentScale, @MagnificationState int state) {
    magnificationMode = mode;
    magnificationCurrentScale = currentScale;
    magnificationState = state;
  }

  public void setSelectionModeActive(boolean value) {
    mSelectionModeActive = value;
  }

  public void setLastTextEditIsPassword(boolean value) {
    mLastTextEditIsPassword = value;
  }

  /** Returns the last text editing field is password or not. */
  public boolean getLastTextEditIsPassword() {
    return mLastTextEditIsPassword;
  }

  /** Returns and clears the state of skip-selection state flags. */
  public boolean resettingNodeCursor() {
    return checkAndClearRecentFlag(EVENT_SKIP_SELECTION_CHANGED_AFTER_FOCUSED)
        || checkAndClearRecentFlag(EVENT_SKIP_SELECTION_CHANGED_AFTER_CURSOR_RESET);
  }

  /**
   * Set by SpeakPasswordsManager. Incorporates service-level speak-passwords preference and
   * headphone state.
   */
  public void setSpeakPasswords(boolean shouldSpeakPasswords) {
    mShouldSpeakPasswords = shouldSpeakPasswords;
  }

  /** Used internally and by TextEventInterpreter. */
  public boolean shouldSpeakPasswords() {
    if (FeatureSupport.useSpeakPasswordsServicePref()) {
      return mShouldSpeakPasswords;
    } else {
      return SettingsCompatUtils.SecureCompatUtils.shouldSpeakPasswords(mContext);
    }
  }

  /** Used by the hint decision. */
  public void setInterpretAsEntryKey(boolean interpretAsEntryKey) {
    isInterpretAsEntryKey = interpretAsEntryKey;
  }

  public void setSpeakCollectionInfo(boolean value) {
    speakCollectionInfo = value;
  }

  public void setSpeakRoles(boolean value) {
    speakRoles = value;
  }

  public void setSpeakSystemWindowTitles(boolean value) {
    speakSystemWindowTitles = value;
  }

  public void setDescriptionOrder(@DescriptionOrder int value) {
    descriptionOrder = value;
  }

  public void setSpeakElementIds(boolean value) {
    speakElementIds = value;
  }

  @Override
  public boolean getBoolean(int variableId) {
    switch (variableId) {
        // Globals
      case GLOBAL_SYNCED_ACCESSIBILITY_FOCUS_LATCH:
        return checkAndClearRecentFlag(EVENT_SYNCED_ACCESSIBILITY_FOCUS);
      case GLOBAL_IS_KEYBOARD_ACTIVE:
        return KeyboardUtils.isKeyboardActive(mService);
      case GLOBAL_IS_SELECTION_MODE_ACTIVE:
        return mSelectionModeActive;
      case GLOBAL_SPEAK_PASS_SERVICE_POLICY:
        return mShouldSpeakPasswords;
      case GLOBAL_SPEAK_PASS_FIELD_CONTENT:
        // Password field content is available only on android N-, and available only based on
        // system setting, regardless of headphones state.
        return shouldSpeakPasswords() && !FeatureSupport.useSpeakPasswordsServicePref();
      case GLOBAL_USE_SINGLE_TAP:
        return mUseSingleTap;
      case GLOBAL_USE_AUDIO_FOCUS:
        return mUseAudioFocus;
      case GLOBAL_LAST_TEXT_EDIT_IS_PASSWORD:
        return mLastTextEditIsPassword;
      case GLOBAL_ENABLE_USAGE_HINT:
        return usageHintEnabled;

        // Collections
      case COLLECTION_EXISTS:
        return mCollectionState.doesCollectionExist();
      case COLLECTION_IS_ROW_TRANSITION:
        return (mCollectionState.getRowColumnTransition() & CollectionState.TYPE_ROW) != 0;
      case COLLECTION_IS_COLUMN_TRANSITION:
        return (mCollectionState.getRowColumnTransition() & CollectionState.TYPE_COLUMN) != 0;
      case COLLECTION_LIST_ITEM_IS_HEADING:
        {
          CollectionState.ListItemState itemState = mCollectionState.getListItemState();
          return itemState != null && itemState.isHeading();
        }
      case COLLECTION_PAGER_ITEM_IS_HEADING:
        {
          CollectionState.PagerItemState itemState = mCollectionState.getPagerItemState();
          return (itemState != null) && itemState.isHeading();
        }

        // Windows
      case WINDOWS_IS_SPLIT_SCREEN_MODE:
        return mWindowsDelegate != null && mWindowsDelegate.isSplitScreenMode(currentDisplayId);

        // Focus
      case FOCUS_IS_CURRENT_FOCUS_IN_SCROLLABLE_NODE:
        return mIsCurrentFocusInScrollableNode;
      case FOCUS_IS_LAST_FOCUS_IN_SCROLLABLE_NODE:
        return mIsLastFocusInScrollableNode;

        // KeyComboManager
      case KEY_COMBO_HAS_KEY_FOR_CLICK:
        return getKeyComboCodeForKey(R.string.keycombo_shortcut_perform_click)
            != KeyComboModel.KEY_COMBO_CODE_UNASSIGNED;
      case KEY_COMBO_HAS_KEY_FOR_LONG_CLICK:
        return getKeyComboCodeForKey(R.string.keycombo_shortcut_perform_long_click)
            != KeyComboModel.KEY_COMBO_CODE_UNASSIGNED;
      case GLOBAL_INTERPRET_AS_ENTRY_KEY:
        return isInterpretAsEntryKey;

        // Verbosity
      case VERBOSITY_SPEAK_ROLES:
        return speakRoles;
      case VERBOSITY_SPEAK_COLLECTION_INFO:
        return speakCollectionInfo;
      case VERBOSITY_SPEAK_ELEMENT_IDS:
        return speakElementIds;
      case VERBOSITY_SPEAK_SYSTEM_WINDOW_TITLES:
        return speakSystemWindowTitles;
      default:
        return false;
    }
  }

  @Override
  public int getInteger(int variableId) {
    switch (variableId) {
      case COLLECTION_TABLE_ITEM_ROW_INDEX:
        {
          CollectionState.TableItemState itemState = mCollectionState.getTableItemState();
          return itemState != null ? itemState.getRowIndex() : -1;
        }
      case COLLECTION_TABLE_ITEM_COLUMN_INDEX:
        {
          CollectionState.TableItemState itemState = mCollectionState.getTableItemState();
          return itemState != null ? itemState.getColumnIndex() : -1;
        }
      case COLLECTION_PAGER_ITEM_ROW_INDEX:
        {
          CollectionState.PagerItemState itemState = mCollectionState.getPagerItemState();
          return (itemState == null) ? -1 : itemState.getRowIndex();
        }
      case COLLECTION_PAGER_ITEM_COLUMN_INDEX:
        {
          CollectionState.PagerItemState itemState = mCollectionState.getPagerItemState();
          return (itemState == null) ? -1 : itemState.getColumnIndex();
        }
      case WINDOWS_LAST_WINDOW_ID:
        return mLastWindowId;
      default:
        return 0;
    }
  }

  @Override
  public double getNumber(int variableId) {
    switch (variableId) {
      case GLOBAL_SPEECH_RATE_CHANGE:
        return mSpeechRate;
      default:
        return 0;
    }
  }

  @Override
  public @Nullable CharSequence getString(int variableId) {
    switch (variableId) {
      case COLLECTION_TRANSITION:
        return getCollectionTransition();
      case COLLECTION_NAME:
        return mCollectionState.getCollectionName();
      case COLLECTION_TABLE_ITEM_ROW_NAME:
        {
          CollectionState.TableItemState itemState = mCollectionState.getTableItemState();
          return itemState != null ? itemState.getRowName() : "";
        }
      case COLLECTION_TABLE_ITEM_COLUMN_NAME:
        {
          CollectionState.TableItemState itemState = mCollectionState.getTableItemState();
          return itemState != null ? itemState.getColumnName() : "";
        }
      case KEY_COMBO_STRING_FOR_CLICK:
        {
          return getKeyComboStringRepresentation(
              getKeyComboCodeForKey(R.string.keycombo_shortcut_perform_click));
        }
      case KEY_COMBO_STRING_FOR_LONG_CLICK:
        {
          return getKeyComboStringRepresentation(
              getKeyComboCodeForKey(R.string.keycombo_shortcut_perform_long_click));
        }
      case GESTURE_STRING_FOR_NODE_ACTIONS:
        {
          if (mInputModeManager.getInputMode() == INPUT_MODE_KEYBOARD) {
            @Nullable CharSequence keyCombo =
                getKeyComboStringRepresentation(
                    getKeyComboCodeForKey(R.string.keycombo_shortcut_other_talkback_context_menu));
            if (!TextUtils.isEmpty(keyCombo)) {
              return keyCombo;
            }
          }
          return gestureShortcutProvider != null ? gestureShortcutProvider.nodeMenuShortcut() : "";
        }
      case COLLECTION_LIST_ITEM_POSITION_DESCRIPTION:
        {
          int rowCount = mCollectionState.getCollectionRowCount();
          int colCount = mCollectionState.getCollectionColumnCount();
          CollectionState.ListItemState itemState = mCollectionState.getListItemState();
          int itemIndex = itemState != null ? itemState.getIndex() : -1;
          // Order of row and column checks does not matter since a list should have either the row
          // or column count populated with > 1 but not both. Otherwise it's a grid.
          if (itemIndex >= 0 && colCount > 1 && rowCount != -1) {
            return mContext.getString(R.string.list_index_template, itemIndex + 1, colCount);
          }
          if (itemIndex >= 0 && rowCount > 1 && colCount != -1) {
            return mContext.getString(R.string.list_index_template, itemIndex + 1, rowCount);
          }
          return "";
        }
      case GLOBAL_SEEKBAR_HINT:
        @Nullable CharSequence result = null;
        if (gestureShortcutProvider != null) {
          result = gestureShortcutProvider.nodeSeekBarShortcut();
        }
        if (result == null) {
          result =
              FeatureSupport.isWatch(mContext)
                  ? ""
                  : mContext.getString(R.string.template_hint_seek_control);
        }
        return result;

      case MAGNIFICATION_STATE_CHANGED:
        int currentScale = (int) (magnificationCurrentScale * 100);
        if (magnificationState == ProcessorMagnification.STATE_ON) {
          if (magnificationMode == null) {
            return mContext.getString(R.string.template_magnification_on, currentScale);
          } else if (magnificationMode.equals(MAGNIFICATION_MODE_FULLSCREEN)) {
            return mContext.getString(R.string.template_fullscreen_magnification_on, currentScale);
          } else if (magnificationMode.equals(MAGNIFICATION_MODE_WINDOW)) {
            return mContext.getString(R.string.template_partial_magnification_on, currentScale);
          }
        } else if (magnificationState == STATE_OFF) {
          return mContext.getString(R.string.magnification_off);
        } else if (magnificationState == ProcessorMagnification.STATE_SCALE_CHANGED) {
          if (magnificationMode == null) {
            return mContext.getString(R.string.template_magnification_scale_changed, currentScale);
          } else if (magnificationMode.equals(MAGNIFICATION_MODE_FULLSCREEN)) {
            return mContext.getString(
                R.string.template_fullscreen_magnification_scale_changed, currentScale);
          } else if (magnificationMode.equals(MAGNIFICATION_MODE_WINDOW)) {
            return mContext.getString(
                R.string.template_partial_magnification_scale_changed, currentScale);
          }
        }
        return "";
      default:
        return "";
    }
  }

  private CharSequence getCollectionTransition() {
    switch (mCollectionState.getCollectionTransition()) {
      case CollectionState.NAVIGATE_ENTER:
        if (mCollectionState.getCollectionRoleDescription() == null) {
          switch (mCollectionState.getCollectionRole()) {
            case Role.ROLE_LIST:
              return joinCharSequences(
                  getCollectionName(R.string.in_list, R.string.in_list_with_name),
                  getCollectionLevel(),
                  getCollectionListItemCount());
            case Role.ROLE_GRID:
              return joinCharSequences(
                  getCollectionName(R.string.in_grid, R.string.in_grid_with_name),
                  getCollectionLevel(),
                  getCollectionGridItemCount());
            case Role.ROLE_PAGER:
              // A pager with a CollectionInfo with 0 or 1 elements will never get to this point
              // because CollectionState#shouldEnter returns false beforehand. This case handles
              // a ViewPager1 which doesn't have a CollectionInfo (rowCount and columnCount do
              // not exist and are set to -1) but CollectionState#shouldEnter returns true
              // because it has >2 children. It may or may not have a name.
              if (!hasAnyCount()) {
                return getCollectionName(R.string.in_pager, R.string.in_pager_with_name);
              } else if (mCollectionState.getCollectionRowCount() > 1
                  && mCollectionState.getCollectionColumnCount() > 1) {
                return getCollectionGridPagerEnter();
              } else if (isVerticalAligned()) {
                return getCollectionVerticalPagerEnter();
              } else {
                return getCollectionHorizontalPagerEnter();
              }
            default:
              return "";
          }
        } else { // has getCollectionRoleDescription
          LogUtils.v(
              TAG,
              "Collection role description is %s",
              mCollectionState.getCollectionRoleDescription());
          CharSequence itemCountCharSequence = "";
          switch (mCollectionState.getCollectionRole()) {
            case Role.ROLE_LIST:
              itemCountCharSequence = getCollectionListItemCount();
              break;
            case Role.ROLE_GRID:
              itemCountCharSequence = getCollectionGridItemCount();
              break;
            case Role.ROLE_PAGER:
              if (hasBothCount()) {
                itemCountCharSequence = getCollectionGridPagerEnter();
              } else if (hasAnyCount()) {
                if (isVerticalAligned()) {
                  itemCountCharSequence = getCollectionVerticalPagerEnter();
                } else {
                  itemCountCharSequence = getCollectionHorizontalPagerEnter();
                }
              } else {
                // has no count
                itemCountCharSequence = getCollectionNameWithRoleDescriptionEnter();
              }
              break;
            default: // Fall out
          }
          return joinCharSequences(
              getCollectionNameWithRoleDescriptionEnter(),
              getCollectionLevel(),
              itemCountCharSequence);
        }
      case CollectionState.NAVIGATE_EXIT:
        if (mCollectionState.getCollectionRoleDescription() == null) {
          switch (mCollectionState.getCollectionRole()) {
            case Role.ROLE_LIST:
              return getCollectionName(R.string.out_of_list, R.string.out_of_list_with_name);
            case Role.ROLE_GRID:
              return getCollectionName(R.string.out_of_grid, R.string.out_of_grid_with_name);
            case Role.ROLE_PAGER:
              if (hasBothCount()) {
                return getCollectionName(
                    R.string.out_of_grid_pager, R.string.out_of_grid_pager_with_name);
              } else if (hasAnyCount()) {
                if (isVerticalAligned()) {
                  return getCollectionName(
                      R.string.out_of_vertical_pager, R.string.out_of_vertical_pager_with_name);
                } else {
                  return getCollectionName(
                      R.string.out_of_horizontal_pager, R.string.out_of_horizontal_pager);
                }
              } else {
                // no count
                return getCollectionName(R.string.out_of_pager, R.string.out_of_pager_with_name);
              }
            default:
              return "";
          }
        } else { // has getCollectionRoleDescription
          LogUtils.v(
              TAG,
              "Collection role description is %s",
              mCollectionState.getCollectionRoleDescription());
          return getCollectionNameWithRoleDescriptionExit();
        }
      default:
        return "";
    }
  }

  private CharSequence getCollectionLevel() {
    if (mCollectionState.getCollectionLevel() >= 0) {
      return mContext.getString(
          R.string.template_collection_level, mCollectionState.getCollectionLevel() + 1);
    }
    return "";
  }

  private boolean isVerticalAligned() {
    return mCollectionState.getCollectionAlignment() == CollectionState.ALIGNMENT_VERTICAL;
  }

  private boolean isHorizontalAligned() {
    return mCollectionState.getCollectionAlignment() == CollectionState.ALIGNMENT_HORIZONTAL;
  }

  private CharSequence getCollectionListItemCount() {
    if (hasBothCount()) {
      if (isVerticalAligned() && mCollectionState.getCollectionRowCount() >= 0) {
        return quantityCharSequence(
            R.plurals.template_list_total_count,
            mCollectionState.getCollectionRowCount(),
            mCollectionState.getCollectionRowCount());
      } else if (isHorizontalAligned() && mCollectionState.getCollectionColumnCount() >= 0) {
        return quantityCharSequence(
            R.plurals.template_list_total_count,
            mCollectionState.getCollectionColumnCount(),
            mCollectionState.getCollectionColumnCount());
      }
    }
    return "";
  }

  private CharSequence quantityCharSequence(int resourceId, int count1, int count2) {
    return mContext.getResources().getQuantityString(resourceId, count1, count2);
  }

  private CharSequence getCollectionGridItemCount() {
    if (hasBothCount()) {
      return joinCharSequences(
          quantityCharSequence(
              R.plurals.template_list_row_count,
              mCollectionState.getCollectionRowCount(),
              mCollectionState.getCollectionRowCount()),
          quantityCharSequence(
              R.plurals.template_list_column_count,
              mCollectionState.getCollectionColumnCount(),
              mCollectionState.getCollectionColumnCount()));
    }
    return "";
  }

  private CharSequence getCollectionName(int stringResId, int withNameStringResId) {
    if (mCollectionState.getCollectionName() == null) {
      return mContext.getString(stringResId);
    } else {
      return mContext.getString(withNameStringResId, mCollectionState.getCollectionName());
    }
  }

  private CharSequence getCollectionNameWithRoleDescriptionEnter() {
    if (TextUtils.isEmpty(mCollectionState.getCollectionRoleDescription())) {
      return EMPTY_STRING;
    }
    if (mCollectionState.getCollectionName() != null) {
      return mContext.getString(
          R.string.in_collection_role_description_with_name,
          mCollectionState.getCollectionRoleDescription(),
          mCollectionState.getCollectionName());
    } else {
      return mContext.getString(
          R.string.in_collection_role_description, mCollectionState.getCollectionRoleDescription());
    }
  }

  private CharSequence getCollectionNameWithRoleDescriptionExit() {
    if (TextUtils.isEmpty(mCollectionState.getCollectionRoleDescription())) {
      return EMPTY_STRING;
    }
    if (mCollectionState.getCollectionName() != null) {
      return mContext.getString(
          R.string.out_of_role_description_with_name,
          mCollectionState.getCollectionRoleDescription(),
          mCollectionState.getCollectionName());
    } else {
      return mContext.getString(
          R.string.out_of_role_description, mCollectionState.getCollectionRoleDescription());
    }
  }

  private static CharSequence joinCharSequences(@Nullable CharSequence... list) {
    List<CharSequence> arrayList = new ArrayList<>(list.length);
    for (CharSequence charSequence : list) {
      if (charSequence != null) {
        arrayList.add(charSequence);
      }
    }
    return ParseTreeJoinNode.joinCharSequences(arrayList, SEPARATOR, PRUNE_EMPTY);
  }

  private CharSequence getCollectionGridPagerEnter() {
    return joinCharSequences(
        getCollectionName(R.string.in_grid_pager, R.string.in_grid_pager_with_name),
        getCollectionLevel(),
        hasBothCount()
            ? joinCharSequences(
                mContext.getString(
                    R.string.row_index_template, getInteger(COLLECTION_TABLE_ITEM_ROW_INDEX) + 1),
                mContext.getString(
                    R.string.column_index_template,
                    getInteger(COLLECTION_TABLE_ITEM_COLUMN_INDEX) + 1))
            : null,
        quantityCharSequence(
            R.plurals.template_list_row_count,
            mCollectionState.getCollectionRowCount(),
            mCollectionState.getCollectionRowCount()),
        quantityCharSequence(
            R.plurals.template_list_column_count,
            mCollectionState.getCollectionColumnCount(),
            mCollectionState.getCollectionColumnCount()));
  }

  private CharSequence getCollectionVerticalPagerEnter() {
    int tableItemRowIndex = getInteger(COLLECTION_TABLE_ITEM_ROW_INDEX);
    return joinCharSequences(
        getCollectionName(R.string.in_vertical_pager, R.string.in_vertical_pager_with_name),
        getCollectionLevel(),
        tableItemRowIndex >= 0
            ? mContext.getString(
                R.string.template_viewpager_index_count,
                tableItemRowIndex + 1,
                mCollectionState.getCollectionRowCount())
            : null);
  }

  private CharSequence getCollectionHorizontalPagerEnter() {
    int tableItemColumnIndex = getInteger(COLLECTION_TABLE_ITEM_COLUMN_INDEX);
    return joinCharSequences(
        getCollectionName(R.string.in_horizontal_pager, R.string.in_horizontal_pager_with_name),
        getCollectionLevel(),
        tableItemColumnIndex >= 0
            ? mContext.getString(
                R.string.template_viewpager_index_count,
                tableItemColumnIndex + 1,
                mCollectionState.getCollectionColumnCount())
            : null);
  }

  private boolean hasAnyCount() {
    return mCollectionState.getCollectionRowCount() > -1
        || mCollectionState.getCollectionColumnCount() > -1;
  }

  private boolean hasBothCount() {
    return mCollectionState.getCollectionRowCount() > -1
        && mCollectionState.getCollectionColumnCount() > -1;
  }

  @Override
  public int getEnum(int variableId) {
    switch (variableId) {
      case GLOBAL_INPUT_MODE:
        return mInputModeManager.getInputMode();
      case COLLECTION_ROLE:
        return mCollectionState.getCollectionRole();
      case COLLECTION_TABLE_ITEM_HEADING_TYPE:
        {
          CollectionState.TableItemState itemState = mCollectionState.getTableItemState();
          return itemState != null ? itemState.getHeadingType() : 0;
        }
      case VERBOSITY_DESCRIPTION_ORDER:
        return descriptionOrder;
      default:
        return 0;
    }
  }

  @Override
  public @Nullable VariableDelegate getReference(int variableId) {
    return null;
  }

  @Override
  public int getArrayLength(int variableId) {
    return 0;
  }

  @Override
  public @Nullable CharSequence getArrayStringElement(int variableId, int index) {
    return "";
  }

  @Override
  public @Nullable VariableDelegate getArrayChildElement(int variableId, int index) {
    return null;
  }

  // TODO: Move this function into utils.
  private long getKeyComboCodeForKey(int keyStringResId) {
    if (mKeyComboManager != null) {
      return mKeyComboManager
          .getKeyComboModel()
          .getKeyComboCodeForKey(mContext.getString(keyStringResId));
    } else {
      return KeyComboModel.KEY_COMBO_CODE_UNASSIGNED;
    }
  }

  // TODO: Move this function into utils.
  private String getKeyComboStringRepresentation(long keyComboCode) {
    if (mKeyComboManager == null) {
      return "";
    }
    KeyComboModel keyComboModel = mKeyComboManager.getKeyComboModel();
    long keyComboCodeWithTriggerModifier =
        KeyComboManager.getKeyComboCode(
            KeyComboManager.getModifier(keyComboCode) | keyComboModel.getTriggerModifier(),
            KeyComboManager.getKeyCode(keyComboCode));

    return mKeyComboManager.getKeyComboStringRepresentation(keyComboCodeWithTriggerModifier);
  }

  ///////////////////////////////////////////////////////////////////////////////////////////
  // Functions callable from compositor script.

  @UsedByReflection("compositor.json")
  private CharSequence cleanUp(CharSequence text) {
    return SpeechCleanupUtils.cleanUp(mContext, text);
  }

  @UsedByReflection("compositor.json")
  private @Nullable CharSequence collapseRepeatedCharactersAndCleanUp(CharSequence text) {
    return SpeechCleanupUtils.collapseRepeatedCharactersAndCleanUp(mContext, text);
  }

  // TODO: Add functionality to ParseTree to support this natively.
  @UsedByReflection("compositor.json")
  private static CharSequence conditionalAppend(
      CharSequence conditionalText, CharSequence appendText) {
    if (TextUtils.isEmpty(conditionalText)) {
      return "";
    }
    if (TextUtils.isEmpty(appendText)) {
      return conditionalText;
    }
    SpannableStringBuilder result = new SpannableStringBuilder();
    result.append(conditionalText);
    result.append(SpannableUtils.wrapWithIdentifierSpan(", "));
    result.append(appendText);
    return result;
  }

  // TODO: Add functionality to ParseTree to support this natively.
  @UsedByReflection("compositor.json")
  private static CharSequence conditionalPrepend(
      CharSequence prependText, CharSequence conditionalText) {
    if (TextUtils.isEmpty(conditionalText)) {
      return "";
    }
    if (TextUtils.isEmpty(prependText)) {
      return conditionalText;
    }
    SpannableStringBuilder result = new SpannableStringBuilder();
    result.append(prependText);
    result.append(SpannableUtils.wrapWithIdentifierSpan(", "));
    result.append(conditionalText);
    return result;
  }

  @UsedByReflection("compositor.json")
  private static CharSequence dedupJoin(
      CharSequence value1, CharSequence value2, CharSequence value3) {
    CharSequence[] values = {value1, value2, value3};
    SpannableStringBuilder builder = new SpannableStringBuilder();
    HashSet<String> uniqueValues = new HashSet<>();
    boolean first = true;
    for (CharSequence value : values) {
      if (TextUtils.isEmpty(value)) {
        continue;
      }
      String lvalue = Ascii.toLowerCase(value.toString());
      if (uniqueValues.contains(lvalue)) {
        continue;
      }
      uniqueValues.add(lvalue);
      if (first) {
        first = false;
      } else {
        // We have to wrap each separator with a different span, because a single span object
        // can only be used once in a CharSequence. An IdentifierSpan indicates the text is a
        // separator, and the text will not be announced.
        builder.append(SpannableUtils.wrapWithIdentifierSpan(", "));
      }
      builder.append(value);
    }
    return builder;
  }

  // TODO: The best way to implement this is to take the separator as an input parameter
  // of the function. However, compositor does not allow hard coded string as parameter of function.
  // Merge this function with conditionalPrepend when the feature supported.
  @UsedByReflection("compositor.json")
  private static CharSequence conditionalPrependWithSpaceSeparator(
      CharSequence prependText, CharSequence conditionalText) {
    if (TextUtils.isEmpty(conditionalText)) {
      return "";
    }
    if (TextUtils.isEmpty(prependText)) {
      return conditionalText;
    }
    SpannableStringBuilder result = new SpannableStringBuilder();
    result.append(prependText);
    result.append(SpannableUtils.wrapWithIdentifierSpan(" "));
    result.append(conditionalText);
    return result;
  }

  @UsedByReflection("compositor.json")
  private CharSequence spelling(CharSequence word) {
    if (word.length() <= 1) {
      return "";
    }

    StringBuilder chars = new StringBuilder();
    for (int i = 0; i < word.length(); i++) {
      final CharSequence character = Character.toString(word.charAt(i));
      final CharSequence cleaned = SpeechCleanupUtils.cleanUp(mContext, character);
      chars.append(cleaned);
    }
    return chars;
  }

  @UsedByReflection("compositor.json")
  private static int round(double value) {
    return (int) Math.round(value);
  }

  @UsedByReflection("compositor.json")
  private static int roundForProgressPercent(double value) {
    return AccessibilityNodeInfoUtils.roundForProgressPercent(value);
  }

  @UsedByReflection("compositor.json")
  private static int roundForProgressInt(double value) {
    return (int) (value);
  }

  @UsedByReflection("compositor.json")
  private CharSequence prependCapital(CharSequence s) {
    if (TextUtils.isEmpty(s) || !sayCapital) {
      return s;
    }
    if ((s.length() == 1) && Character.isUpperCase(s.charAt(0))) {
      return mContext.getString(R.string.template_capital_letter, s.charAt(0));
    }
    return s;
  }

  @UsedByReflection("compositor.json")
  private CharSequence getWindowTitle(int windowId) {
    if (mWindowsDelegate == null) {
      return "";
    }

    CharSequence title = mWindowsDelegate.getWindowTitle(windowId);
    return title != null ? title : "";
  }

  @UsedByReflection("compositor.json")
  private static boolean equals(CharSequence text1, CharSequence text2) {
    return TextUtils.equals(text1, text2);
  }
}
