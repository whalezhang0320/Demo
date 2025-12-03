package com.example.star.aiwork.ui.market

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import com.example.star.aiwork.ui.theme.JetchatTheme

class PromptMarketFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                JetchatTheme {
                    PromptMarketScreen(
                        onBack = { findNavController().popBackStack() }
                    )
                }
            }
        }
    }
}
