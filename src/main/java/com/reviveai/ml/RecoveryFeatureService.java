package com.reviveai.ml;

import com.reviveai.entity.RecoveryCase;

public interface RecoveryFeatureService {

    RecoveryFeatures extractFeatures(
            RecoveryCase recoveryCase
    );
}