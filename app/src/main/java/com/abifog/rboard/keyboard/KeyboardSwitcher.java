/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.abifog.rboard.keyboard;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import com.abifog.rboard.R;
import com.abifog.rboard.event.Event;
import com.abifog.rboard.keyboard.KeyboardLayoutSet.KeyboardLayoutSetException;
import com.abifog.rboard.keyboard.internal.KeyboardState;
import com.abifog.rboard.keyboard.internal.KeyboardTextsSet;
import com.abifog.rboard.latin.InputView;
import com.abifog.rboard.latin.LatinIME;
import com.abifog.rboard.latin.RichInputMethodManager;
import com.abifog.rboard.latin.settings.Settings;
import com.abifog.rboard.latin.settings.SettingsValues;
import com.abifog.rboard.latin.utils.CapsModeUtils;
import com.abifog.rboard.latin.utils.LanguageOnSpacebarUtils;
import com.abifog.rboard.latin.utils.RecapitalizeStatus;
import com.abifog.rboard.latin.utils.ResourceUtils;

public final class KeyboardSwitcher implements KeyboardState.SwitchActions {
    private static final String TAG = KeyboardSwitcher.class.getSimpleName();

    private InputView mCurrentInputView;
    private View mMainKeyboardFrame;
    private MainKeyboardView mKeyboardView;
    private android.widget.ViewFlipper mKeyboardFlipper;
    private View mEmojiPanel;
    private View mGifPanel;
    private LatinIME mLatinIME;
    private RichInputMethodManager mRichImm;

    private KeyboardState mState;

    private KeyboardLayoutSet mKeyboardLayoutSet;
    // TODO: The following {@link KeyboardTextsSet} should be in {@link KeyboardLayoutSet}.
    private final KeyboardTextsSet mKeyboardTextsSet = new KeyboardTextsSet();

    private KeyboardTheme mKeyboardTheme;
    private Context mThemeContext;

    private static final KeyboardSwitcher sInstance = new KeyboardSwitcher();

    public static KeyboardSwitcher getInstance() {
        return sInstance;
    }

    private KeyboardSwitcher() {
        // Intentional empty constructor for singleton.
    }

    public static void init(final LatinIME latinIme) {
        sInstance.initInternal(latinIme);
    }

    private void initInternal(final LatinIME latinIme) {
        mLatinIME = latinIme;
        mRichImm = RichInputMethodManager.getInstance();
        mState = new KeyboardState(this);
    }

    public void updateKeyboardTheme() {
        final boolean themeUpdated = updateKeyboardThemeAndContextThemeWrapper(
                mLatinIME, KeyboardTheme.getKeyboardTheme(mLatinIME /* context */));
        if (themeUpdated && mKeyboardView != null) {
            mLatinIME.setInputView(onCreateInputView());
        }
    }

    private boolean updateKeyboardThemeAndContextThemeWrapper(final Context context,
            final KeyboardTheme keyboardTheme) {
        if (mThemeContext == null || !keyboardTheme.equals(mKeyboardTheme)) {
            mKeyboardTheme = keyboardTheme;
            mThemeContext = new ContextThemeWrapper(context, keyboardTheme.mStyleId);
            KeyboardLayoutSet.onKeyboardThemeChanged();
            return true;
        }
        return false;
    }

    public void loadKeyboard(final EditorInfo editorInfo, final SettingsValues settingsValues,
            final int currentAutoCapsState, final int currentRecapitalizeState) {
        final KeyboardLayoutSet.Builder builder = new KeyboardLayoutSet.Builder(
                mThemeContext, editorInfo);
        final Resources res = mThemeContext.getResources();
        final int keyboardWidth = mLatinIME.getMaxWidth();
        final int keyboardHeight = ResourceUtils.getKeyboardHeight(res, settingsValues);
        builder.setKeyboardGeometry(keyboardWidth, keyboardHeight);
        builder.setSubtype(mRichImm.getCurrentSubtype());
        builder.setLanguageSwitchKeyEnabled(mLatinIME.shouldShowLanguageSwitchKey());
        builder.setShowSpecialChars(!settingsValues.mHideSpecialChars);
        builder.setShowNumberRow(settingsValues.mShowNumberRow);
        mKeyboardLayoutSet = builder.build();
        try {
            mState.onLoadKeyboard(currentAutoCapsState, currentRecapitalizeState);
            mKeyboardTextsSet.setLocale(mRichImm.getCurrentSubtypeLocale(), mThemeContext);
        } catch (KeyboardLayoutSetException e) {
            Log.w(TAG, "loading keyboard failed: " + e.mKeyboardId, e.getCause());
        }
    }

    public void saveKeyboardState() {
        if (getKeyboard() != null) {
            mState.onSaveKeyboardState();
        }
    }

    public void onHideWindow() {
        if (mKeyboardView != null) {
            mKeyboardView.onHideWindow();
        }
    }

    private void setKeyboard(
            final int keyboardId,
            final KeyboardSwitchState toggleState) {
        final SettingsValues currentSettingsValues = Settings.getInstance().getCurrent();
        setMainKeyboardFrame(currentSettingsValues, toggleState);
        // TODO: pass this object to setKeyboard instead of getting the current values.
        final MainKeyboardView keyboardView = mKeyboardView;
        final Keyboard oldKeyboard = keyboardView.getKeyboard();
        final Keyboard newKeyboard = mKeyboardLayoutSet.getKeyboard(keyboardId);
        keyboardView.setKeyboard(newKeyboard);
        keyboardView.setKeyPreviewPopupEnabled(
                currentSettingsValues.mKeyPreviewPopupOn,
                currentSettingsValues.mKeyPreviewPopupDismissDelay);
        keyboardView.updateShortcutKey(mRichImm.isShortcutImeReady());
        final boolean subtypeChanged = (oldKeyboard == null)
                || !newKeyboard.mId.mSubtype.equals(oldKeyboard.mId.mSubtype);
        final int languageOnSpacebarFormatType = LanguageOnSpacebarUtils
                .getLanguageOnSpacebarFormatType(newKeyboard.mId.mSubtype);
        keyboardView.startDisplayLanguageOnSpacebar(subtypeChanged, languageOnSpacebarFormatType);
    }

    public Keyboard getKeyboard() {
        if (mKeyboardView != null) {
            return mKeyboardView.getKeyboard();
        }
        return null;
    }

    // TODO: Remove this method. Come up with a more comprehensive way to reset the keyboard layout
    // when a keyboard layout set doesn't get reloaded in LatinIME.onStartInputViewInternal().
    public void resetKeyboardStateToAlphabet(final int currentAutoCapsState,
            final int currentRecapitalizeState) {
        mState.onResetKeyboardStateToAlphabet(currentAutoCapsState, currentRecapitalizeState);
    }

    public void onPressKey(final int code, final boolean isSinglePointer,
            final int currentAutoCapsState, final int currentRecapitalizeState) {
        mState.onPressKey(code, isSinglePointer, currentAutoCapsState, currentRecapitalizeState);
    }

    public void onReleaseKey(final int code, final boolean withSliding,
            final int currentAutoCapsState, final int currentRecapitalizeState) {
        mState.onReleaseKey(code, withSliding, currentAutoCapsState, currentRecapitalizeState);
    }

    public void onFinishSlidingInput(final int currentAutoCapsState,
            final int currentRecapitalizeState) {
        mState.onFinishSlidingInput(currentAutoCapsState, currentRecapitalizeState);
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void setAlphabetKeyboard() {
        if (DEBUG_ACTION) {
            Log.d(TAG, "setAlphabetKeyboard");
        }
        setKeyboard(KeyboardId.ELEMENT_ALPHABET, KeyboardSwitchState.OTHER);
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void setAlphabetManualShiftedKeyboard() {
        if (DEBUG_ACTION) {
            Log.d(TAG, "setAlphabetManualShiftedKeyboard");
        }
        setKeyboard(KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED, KeyboardSwitchState.OTHER);
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void setAlphabetAutomaticShiftedKeyboard() {
        if (DEBUG_ACTION) {
            Log.d(TAG, "setAlphabetAutomaticShiftedKeyboard");
        }
        setKeyboard(KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED, KeyboardSwitchState.OTHER);
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void setAlphabetShiftLockedKeyboard() {
        if (DEBUG_ACTION) {
            Log.d(TAG, "setAlphabetShiftLockedKeyboard");
        }
        setKeyboard(KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED, KeyboardSwitchState.OTHER);
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void setAlphabetShiftLockShiftedKeyboard() {
        if (DEBUG_ACTION) {
            Log.d(TAG, "setAlphabetShiftLockShiftedKeyboard");
        }
        setKeyboard(KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED, KeyboardSwitchState.OTHER);
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void setSymbolsKeyboard() {
        if (DEBUG_ACTION) {
            Log.d(TAG, "setSymbolsKeyboard");
        }
        setKeyboard(KeyboardId.ELEMENT_SYMBOLS, KeyboardSwitchState.OTHER);
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void setSymbolsShiftedKeyboard() {
        if (DEBUG_ACTION) {
            Log.d(TAG, "setSymbolsShiftedKeyboard");
        }
        setKeyboard(KeyboardId.ELEMENT_SYMBOLS_SHIFTED, KeyboardSwitchState.SYMBOLS_SHIFTED);
    }

    public boolean isImeSuppressedByHardwareKeyboard(
            final SettingsValues settingsValues,
            final KeyboardSwitchState toggleState) {
        // Force software keyboard to always show, ignoring hardware keyboard state (LokiBoard logic)
        return false;
    }

    private void setMainKeyboardFrame(
            final SettingsValues settingsValues,
            final KeyboardSwitchState toggleState) {
        final int visibility =  isImeSuppressedByHardwareKeyboard(settingsValues, toggleState)
                ? View.GONE : View.VISIBLE;
        mKeyboardView.setVisibility(visibility);
        // The visibility of {@link #mKeyboardView} must be aligned with {@link #MainKeyboardFrame}.
        // @see #getVisibleKeyboardView() and
        // @see LatinIME#onComputeInset(android.inputmethodservice.InputMethodService.Insets)
        mMainKeyboardFrame.setVisibility(visibility);
    }

    public enum KeyboardSwitchState {
        HIDDEN(-1),
        SYMBOLS_SHIFTED(KeyboardId.ELEMENT_SYMBOLS_SHIFTED),
        OTHER(-1);

        final int mKeyboardId;

        KeyboardSwitchState(int keyboardId) {
            mKeyboardId = keyboardId;
        }
    }

    public KeyboardSwitchState getKeyboardSwitchState() {
        boolean hidden = mKeyboardLayoutSet == null
                || mKeyboardView == null
                || !mKeyboardView.isShown();
        if (hidden) {
            return KeyboardSwitchState.HIDDEN;
        } else if (isShowingKeyboardId(KeyboardId.ELEMENT_SYMBOLS_SHIFTED)) {
            return KeyboardSwitchState.SYMBOLS_SHIFTED;
        }
        return KeyboardSwitchState.OTHER;
    }

    // Future method for requesting an updating to the shift state.
    @Override
    public void requestUpdatingShiftState(final int autoCapsFlags, final int recapitalizeMode) {
        if (DEBUG_ACTION) {
            Log.d(TAG, "requestUpdatingShiftState: "
                    + " autoCapsFlags=" + CapsModeUtils.flagsToString(autoCapsFlags)
                    + " recapitalizeMode=" + RecapitalizeStatus.modeToString(recapitalizeMode));
        }
        mState.onUpdateShiftState(autoCapsFlags, recapitalizeMode);
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void startDoubleTapShiftKeyTimer() {
        if (DEBUG_TIMER_ACTION) {
            Log.d(TAG, "startDoubleTapShiftKeyTimer");
        }
        final MainKeyboardView keyboardView = getMainKeyboardView();
        if (keyboardView != null) {
            keyboardView.startDoubleTapShiftKeyTimer();
        }
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void cancelDoubleTapShiftKeyTimer() {
        if (DEBUG_TIMER_ACTION) {
            Log.d(TAG, "setAlphabetKeyboard");
        }
        final MainKeyboardView keyboardView = getMainKeyboardView();
        if (keyboardView != null) {
            keyboardView.cancelDoubleTapShiftKeyTimer();
        }
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public boolean isInDoubleTapShiftKeyTimeout() {
        if (DEBUG_TIMER_ACTION) {
            Log.d(TAG, "isInDoubleTapShiftKeyTimeout");
        }
        final MainKeyboardView keyboardView = getMainKeyboardView();
        return keyboardView != null && keyboardView.isInDoubleTapShiftKeyTimeout();
    }

    /**
     * Updates state machine to figure out when to automatically switch back to the previous mode.
     */
    public void onEvent(final Event event, final int currentAutoCapsState,
            final int currentRecapitalizeState) {
        mState.onEvent(event, currentAutoCapsState, currentRecapitalizeState);
    }

    public boolean isShowingKeyboardId(int... keyboardIds) {
        if (mKeyboardView == null || !mKeyboardView.isShown()) {
            return false;
        }
        int activeKeyboardId = mKeyboardView.getKeyboard().mId.mElementId;
        for (int keyboardId : keyboardIds) {
            if (activeKeyboardId == keyboardId) {
                return true;
            }
        }
        return false;
    }

    public boolean isShowingMoreKeysPanel() {
        return mKeyboardView.isShowingMoreKeysPanel();
    }

    public View getVisibleKeyboardView() {
        return mKeyboardView;
    }

    public MainKeyboardView getMainKeyboardView() {
        return mKeyboardView;
    }

    public void deallocateMemory() {
        if (mKeyboardView != null) {
            mKeyboardView.cancelAllOngoingEvents();
            mKeyboardView.deallocateMemory();
        }
    }

    public View onCreateInputView() {
        if (mKeyboardView != null) {
            mKeyboardView.closing();
        }

        updateKeyboardThemeAndContextThemeWrapper(
                mLatinIME, KeyboardTheme.getKeyboardTheme(mLatinIME /* context */));
        mCurrentInputView = (InputView)LayoutInflater.from(mThemeContext).inflate(
                R.layout.input_view, null);
        mMainKeyboardFrame = mCurrentInputView.findViewById(R.id.main_keyboard_frame);

        mKeyboardView = (MainKeyboardView) mCurrentInputView.findViewById(R.id.keyboard_view);
        mKeyboardView.setKeyboardActionListener(mLatinIME);

        mKeyboardFlipper = (android.widget.ViewFlipper) mCurrentInputView.findViewById(R.id.keyboard_flipper);
        mEmojiPanel = mCurrentInputView.findViewById(R.id.emoji_panel);
        mGifPanel = mCurrentInputView.findViewById(R.id.gif_panel);
        
        setupEmojiPanel();
        setupGifPanel();
        
        // Load background image if set
        loadBackgroundImage(Settings.getInstance().getCurrent());
        
        // Setup toolbar actions
        setupToolbar(mCurrentInputView);
        
        return mCurrentInputView;
    }

    private void loadBackgroundImage(SettingsValues settingsValues) {
        if (mKeyboardView == null || settingsValues == null) return;
        String path = settingsValues.mBackgroundImagePath;
        if (path != null && !path.isEmpty()) {
            try {
                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(path);
                if (bitmap != null) {
                    mKeyboardView.setBackgroundImage(bitmap);
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to load background image: " + path, e);
            }
        }
    }

    private void setupToolbar(View inputView) {
        View expandBtn = inputView.findViewById(R.id.toolbar_expand);
        if (expandBtn != null) {
            expandBtn.setOnClickListener(v -> {
                // Toggle between suggestions and tool icons
            });
        }
        
        View stickersBtn = inputView.findViewById(R.id.toolbar_stickers);
        if (stickersBtn != null) {
            stickersBtn.setOnClickListener(v -> {
                // Open stickers panel
            });
        }
        
        View gifBtn = inputView.findViewById(R.id.toolbar_gif);
        if (gifBtn != null) {
            gifBtn.setOnClickListener(v -> toggleGifPanel());
        }
        
        View clipboardBtn = inputView.findViewById(R.id.toolbar_clipboard);
        if (clipboardBtn != null) {
            clipboardBtn.setOnClickListener(v -> {
                // Open clipboard panel
            });
        }
        
        View settingsBtn = inputView.findViewById(R.id.toolbar_settings);
        if (settingsBtn != null) {
            settingsBtn.setOnClickListener(v -> mLatinIME.launchSettings());
        }
        
        View micBtn = inputView.findViewById(R.id.toolbar_mic);
        if (micBtn != null) {
            micBtn.setOnClickListener(v -> {
                // Start voice input
            });
        }
    }

    private void setupEmojiPanel() {
        if (mEmojiPanel == null) return;
        
        android.widget.GridView emojiGrid = mEmojiPanel.findViewById(R.id.emoji_grid);
        View backBtn = mEmojiPanel.findViewById(R.id.emoji_back);
        
        if (backBtn != null) {
            backBtn.setOnClickListener(v -> toggleEmojiPanel());
        }
        
        if (emojiGrid != null) {
            final String[] commonEmojis = {
                "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇",
                "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚",
                "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🤩",
                "🥳", "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "☹️", "😣",
                "😖", "😫", "😩", "🥺", "😢", "😭", "😤", "😠", "😡", "🤬",
                "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰", "😥", "😓", "🤗",
                "🤔", "🤭", "🤫", "🤥", "😶", "😐", "😑", "😬", "🙄", "😯",
                "😴", "🥱", "😴", "🤤", "😪", "😵", "🤐", "🥴", "🤢", "🤮",
                "🤧", "😷", "🤒", "🤕", "🤑", "🤠", "😈", "👿", "👹", "👺"
            };
            
            android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<String>(
                    mThemeContext, android.R.layout.simple_list_item_1, commonEmojis) {
                @Override
                public View getView(int position, View convertView, android.view.ViewGroup parent) {
                    android.widget.TextView textView = (android.widget.TextView) super.getView(position, convertView, parent);
                    textView.setGravity(android.view.Gravity.CENTER);
                    textView.setTextSize(24);
                    textView.setPadding(0, 12, 0, 12);
                    
                    // Set color based on theme
                    int textColor = android.graphics.Color.BLACK;
                    if (KeyboardTheme.getKeyboardTheme(getContext()).mStyleId == R.style.KeyboardTheme_Gboard_Dark) {
                        textColor = android.graphics.Color.WHITE;
                    }
                    textView.setTextColor(textColor);
                    
                    return textView;
                }
            };
            
            emojiGrid.setAdapter(adapter);
            emojiGrid.setOnItemClickListener((parent, view, position, id) -> {
                String emoji = commonEmojis[position];
                mLatinIME.onTextInput(emoji);
            });
        }
    }

    private void setupGifPanel() {
        if (mGifPanel == null) return;
        
        View backBtn = mGifPanel.findViewById(R.id.gif_back);
        if (backBtn != null) {
            backBtn.setOnClickListener(v -> toggleGifPanel());
        }
        
        // GIF grid population would go here if we had an API
    }

    private void toggleEmojiPanel() {
        if (mKeyboardFlipper == null) return;
        
        if (mKeyboardFlipper.getDisplayedChild() == 0) {
            mKeyboardFlipper.setInAnimation(mThemeContext, R.anim.push_up_in);
            mKeyboardFlipper.setOutAnimation(mThemeContext, R.anim.push_down_out);
            mKeyboardFlipper.setDisplayedChild(1); // Emoji panel
        } else if (mKeyboardFlipper.getDisplayedChild() == 1) {
            mKeyboardFlipper.setInAnimation(mThemeContext, R.anim.push_up_in);
            mKeyboardFlipper.setOutAnimation(mThemeContext, R.anim.push_down_out);
            mKeyboardFlipper.setDisplayedChild(0); // Main keyboard
        }
    }

    private void toggleGifPanel() {
        if (mKeyboardFlipper == null) return;
        
        if (mKeyboardFlipper.getDisplayedChild() == 0) {
            mKeyboardFlipper.setInAnimation(mThemeContext, R.anim.push_up_in);
            mKeyboardFlipper.setOutAnimation(mThemeContext, R.anim.push_down_out);
            mKeyboardFlipper.setDisplayedChild(2); // GIF panel
        } else if (mKeyboardFlipper.getDisplayedChild() == 2) {
            mKeyboardFlipper.setInAnimation(mThemeContext, R.anim.push_up_in);
            mKeyboardFlipper.setOutAnimation(mThemeContext, R.anim.push_down_out);
            mKeyboardFlipper.setDisplayedChild(0); // Main keyboard
        }
    }
}
