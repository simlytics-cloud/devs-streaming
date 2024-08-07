package devs.experimentalframe;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import devs.OutputCouplingHandler;
import devs.msg.PortValue;


public class TestOutputCouplingHandler extends OutputCouplingHandler {

	public TestOutputCouplingHandler() {
		super(Optional.empty(), Optional.empty(), Optional.empty());
	}

	@Override
	public void handlePortValue(String sender, PortValue<?> portValue, Map<String, List<PortValue<?>>> receiverMap,
			List<PortValue<?>> outputMessages) {
		if (portValue.getPortIdentifier().equals("numbers")) {
			Integer number = LogBaseTwoCalculatorModel.numberIn.getValue(portValue);
			PortValue<?> pv = LogBaseTwoCalculatorModel.numberIn.createPortValue(number);
			addInputPortValue(pv, LogBaseTwoCalculatorModel.modelIdentifier, receiverMap);
		} else if (portValue.getPortIdentifier().equals("words")) {
			String word = LogBaseTwoCalculatorModel.wordIn.getValue(portValue);
			PortValue<?> pv = LogBaseTwoCalculatorModel.wordIn.createPortValue(word);
			addInputPortValue(pv, LogBaseTwoCalculatorModel.modelIdentifier, receiverMap);
		} else if (portValue.getPortIdentifier().equals(LogBaseTwoCalculatorModel.numberOut.getPortIdentifier())) {
			Integer number = TestAcceptor.acceptNumber.getValue(portValue);
			PortValue<?> pv = TestAcceptor.acceptNumber.createPortValue(number);
			addInputPortValue(pv, TestAcceptor.modelIdentifier, receiverMap);
		} else if (portValue.getPortIdentifier().equals(LogBaseTwoCalculatorModel.wordOut.getPortIdentifier())) {
			String word = TestAcceptor.acceptWord.getValue(portValue);
			PortValue<?> pv = TestAcceptor.acceptWord.createPortValue(word);
			addInputPortValue(pv, TestAcceptor.modelIdentifier, receiverMap);
		} else {
			throw new IllegalArgumentException("Couplings cannot handle port value with identiifier " + portValue.getPortIdentifier()); 
		}
		
	}



}
