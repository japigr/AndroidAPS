package app.aaps.pump.medtrum.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.lifecycle.ViewModelProvider
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.pump.medtrum.MedtrumPump
import app.aaps.pump.medtrum.R
import app.aaps.pump.medtrum.code.PatchStep
import app.aaps.pump.medtrum.databinding.FragmentMedtrumPrimingBinding
import app.aaps.pump.medtrum.ui.viewmodel.MedtrumViewModel
import java.util.Locale
import javax.inject.Inject

class MedtrumPrimingFragment : MedtrumBaseFragment<FragmentMedtrumPrimingBinding>() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var medtrumPump: MedtrumPump
    @Inject lateinit var dateUtil: DateUtil

    // fork japigr/AndroidAPS: time since the start of priming and the current speed, refreshed every second
    private val liveHandler = Handler(Looper.getMainLooper())
    private val liveUpdate = object : Runnable {
        override fun run() {
            updatePrimeLive()
            liveHandler.postDelayed(this, 1000L)
        }
    }

    companion object {

        fun newInstance(): MedtrumPrimingFragment = MedtrumPrimingFragment()
    }

    override fun getLayoutId(): Int = R.layout.fragment_medtrum_priming

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.apply {
            viewModel = ViewModelProvider(requireActivity(), viewModelFactory)[MedtrumViewModel::class.java]
            viewModel?.apply {
                setupStep.observe(viewLifecycleOwner) {
                    when (it) {
                        MedtrumViewModel.SetupStep.INITIAL,
                        MedtrumViewModel.SetupStep.FILLED,
                        MedtrumViewModel.SetupStep.PRIMING -> Unit // Nothing to do here
                        MedtrumViewModel.SetupStep.PRIMED  -> moveStep(PatchStep.PRIME_COMPLETE)

                        else                               -> {
                            aapsLogger.error(LTag.PUMP, "Unexpected state: $it")
                            OKDialog.show(
                                requireActivity(),
                                rh.gs(app.aaps.core.ui.R.string.error),
                                rh.gs(R.string.unexpected_state, it.toString()),
                                runOnDismiss = true
                            ) {
                                viewModel?.moveStep(PatchStep.CANCEL)
                            }
                        }
                    }
                }
                startPrime()
            }
            btnNegative.setOnClickListener {
                OKDialog.showConfirmation(requireActivity(), rh.gs(R.string.cancel_sure)) {
                    viewModel?.apply {
                        moveStep(PatchStep.CANCEL)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        liveHandler.post(liveUpdate)
    }

    override fun onPause() {
        liveHandler.removeCallbacks(liveUpdate)
        super.onPause()
    }

    private fun updatePrimeLive() {
        val startTime = medtrumPump.primeStartTime
        if (startTime == 0L) {
            binding.primeLive.text = ""
            return
        }
        val endTime = if (medtrumPump.primeEndTime != 0L) medtrumPump.primeEndTime else dateUtil.now()
        val seconds = ((endTime - startTime) / 1000L).coerceAtLeast(0L)
        val speed = medtrumPump.primeStepsPerMinute?.let { String.format(Locale.getDefault(), "%.0f", it) } ?: "-"
        binding.primeLive.text = rh.gs(R.string.prime_live, String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60), speed)
    }
}
