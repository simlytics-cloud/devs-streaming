

## ExperimentalFrameTest Simulation using DEVS

This test contains a coupled DEVS (Discrete Event System Specification) model for **experimental frame testing**. The simulation system is constructed using individual submodels and a coupling structure, all integrated and managed by a `CoupledModelFactory`. The test validates the system's behavior by simulating data generation, processing, and acceptance within a configurable framework.

---

## **Components**

### 1. **PowerOfTwoGenerator**
The `PowerOfTwoGenerator` is a model responsible for generating:
- A sequence of numbers: `1, 2, 4, 8`.
- Corresponding words: `"One", "Two", "Four", "Eight"`.

This data is generated and emitted through the **`numbers`** and **`words`** ports at specific time intervals.

---

### 2. **LogBaseTwoCalculatorModel**
The `LogBaseTwoCalculatorModel` handles both numeric and textual inputs:
- **Numerical input processing:**
    - Receives numbers on the **`numberIn` port**.
    - Computes the base-2 logarithm and emits the integer result to the **`numberOut` port**.
- **Textual input processing:**
    - Receives words on the **`wordIn` port**.
    - Maps incoming words to their logarithmic equivalent:
        - `"One"` → `"Zero"`
        - `"Two"` → `"One"`
        - `"Four"` → `"Two"`
        - `"Eight"` → `"Three"`
    - Emits the transformed output to the **`wordOut` port**.

---

### 3. **TestAcceptor**
The `TestAcceptor` ensures the results produced by the coupled system are correct. While explicit implementation details are not available, this component likely validates:
1. The integrity of numerical and textual outputs.
2. The consistency of the simulation results against expected values.

---

### 4. **Coupled Model Factory**
The simulation system is created using a `CoupledModelFactory`.
- The factory constructs a **coupled DEVS model** named `"experimentalFrameTest"`.
- It groups the submodels (`PowerOfTwoGenerator`, `LogBaseTwoCalculatorModel`, `TestAcceptor`) into an integrated framework.

---

## **Model Interaction and Data Flow**

The interaction between the models is managed by **couplings** using the `PDevsCouplings` class. The data flow follows this path:

1. The `PowerOfTwoGenerator` produces numbers and words based on a pre-defined schedule.
2. The `LogBaseTwoCalculatorModel`:
    - Receives numerical input and processes it to compute logarithmic results.
    - Transforms textual words into their corresponding logarithmic equivalents.
3. The `TestAcceptor` validates the processed outputs from the `LogBaseTwoCalculatorModel`.
4. Couplings are defined within the `PDevsCouplings` class to manage the exchange of data.

---

## **Couplings**

The coupling structure is defined via `PDevsCouplings`:
- **Input Couplings**:
    - Configured as an empty list in this setup (`Collections.emptyList()`), meaning the coupled system does not receive external inputs.
- **Output Couplings**:
    - Handled by the `TestOutputCouplingHandler` to route output data between the models or validate them externally.

---

## **Purpose**

The primary goal of the `ExperimentalFrameTest` is to simulate and validate an experimental frame using the DEVS methodology. The coupled DEVS model includes:
1. **Data Generation:** Performed by the `PowerOfTwoGenerator`.
2. **Data Processing:** Achieved through the `LogBaseTwoCalculatorModel`.
3. **Output Validation:** Facilitated by the `TestAcceptor`.

This system simulates and tests interconnected components by ensuring:
- Data generation and processing are correct.
- Outputs are validated against expected scenarios.
- All components interact as intended.

---

## **How to Use**

Here’s an overview of how the system can be configured and used:

### 1. Instantiate Submodels
Create instances of the generator, calculator, and acceptor:
```java
PowerOfTwoGenerator generator = new PowerOfTwoGenerator();
LogBaseTwoCalculatorModel logCalculator = new LogBaseTwoCalculatorModel();
TestAcceptor testAcceptor = new TestAcceptor();
```

### 2. Group Submodels
Add the submodels to the internal coupled model:
```java
List<PDEVSModel<LongSimTime, ?>> devsModels =
    Arrays.asList(generator, logCalculator, testAcceptor);
```

### 3. Define Couplings
Configure the input and output couplings using `PDevsCouplings`:
```java
PDevsCouplings couplings = new PDevsCouplings(
    Collections.emptyList(), // No input couplings
    Collections.singletonList(new TestOutputCouplingHandler()) // Output handler
);
```

### 4. Construct the Coupled Model
Use the `CoupledModelFactory` to assemble the overall simulation model:
```java
coupledModelFactory = new CoupledModelFactory<>(
    "experimentalFrameTest",  // Coupled model name
    devsModels,               // List of submodels
    Collections.emptyList(),  // Empty input coupling configuration
    couplings                 // Predefined couplings
);
```


