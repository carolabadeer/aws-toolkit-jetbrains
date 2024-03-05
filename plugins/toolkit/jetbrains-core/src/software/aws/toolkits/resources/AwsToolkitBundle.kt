// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.resources

import com.intellij.DynamicBundle
import org.jetbrains.annotations.PropertyKey

object AwsToolkitBundle {
    private const val BUNDLE_FQN: String = "software.aws.toolkits.resources.MessagesBundle"
    private val BUNDLE = DynamicBundle(AwsToolkitBundle::class.java, BUNDLE_FQN)

    fun message(key: @PropertyKey(resourceBundle = BUNDLE_FQN) String, vararg params: Any) =
        BUNDLE.getMessage(key, *params)

    fun messagePointer(key: @PropertyKey(resourceBundle = BUNDLE_FQN) String, vararg params: Any) =
        BUNDLE.getLazyMessage(key, *params)
}
