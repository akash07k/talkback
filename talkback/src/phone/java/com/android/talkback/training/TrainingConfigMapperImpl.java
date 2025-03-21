/*
 * Copyright (C) 2023 Google Inc.
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

package com.google.android.accessibility.talkback.training;

import static com.google.android.accessibility.talkback.training.OnboardingConfigs.getTalkBackOnBoardingForSettings;
import static com.google.android.accessibility.talkback.training.OnboardingConfigs.getTalkBackOnBoardingForUpdated;

import android.content.Context;
import androidx.annotation.Nullable;
import com.google.android.accessibility.talkback.trainingcommon.TrainingActivityInterfaceInjector.TrainingConfigMapper;
import com.google.android.accessibility.talkback.trainingcommon.TrainingConfig;
import com.google.android.accessibility.talkback.trainingcommon.TrainingConfig.TrainingId;
import com.google.android.accessibility.talkback.trainingcommon.tv.TvTutorialInitiator;
import com.google.android.accessibility.talkback.trainingcommon.tv.VendorConfigReader;

/** The implementation of {@link TrainingConfigMapper} for handset platform. */
public final class TrainingConfigMapperImpl implements TrainingConfigMapper {

  @Override
  @Nullable
  public TrainingConfig getTraining(TrainingId trainingId, @Nullable Context context) {
    switch (trainingId) {
      case TRAINING_ID_ON_BOARDING_TALKBACK:
        return getTalkBackOnBoardingForSettings();
      case TRAINING_ID_FIRST_RUN_AFTER_UPDATED_ON_BOARDING_TALKBACK:
        return getTalkBackOnBoardingForUpdated();
      case TRAINING_ID_ON_BOARDING_FOR_MULTIFINGER_GESTURES:
        return OnboardingConfigs.ON_BOARDING_FOR_MULTIFINGER_GESTURES;
      case TRAINING_ID_FIRST_RUN_TUTORIAL:
        return TutorialConfigUtils.getFirstRunTutorial(context);
      case TRAINING_ID_TUTORIAL:
        return TutorialConfigUtils.getTutorialInSettings(context);
      case TRAINING_ID_TUTORIAL_PRACTICE_GESTURE:
        return TutorialConfigUtils.TUTORIAL_PRACTICE_GESTURE;
      case TRAINING_ID_TUTORIAL_PRACTICE_GESTURE_PRE_R:
        return TutorialConfigUtils.TUTORIAL_PRACTICE_GESTURE_PRE_R;
      case TRAINING_ID_TUTORIAL_SPELL_CHECK:
        return TutorialConfigUtils.getSpellCheckTutorial(context);
      case TRAINING_ID_VOICE_COMMAND_HELP:
        return VoiceCommandAndHelpConfigs.VOICE_COMMAND_HELP;
      case TRAINING_ID_TUTORIAL_FOR_TV:
        return TvTutorialInitiator.getTraining(context, VendorConfigReader.retrieveConfig(context));
      default:
        return null;
    }
  }
}
