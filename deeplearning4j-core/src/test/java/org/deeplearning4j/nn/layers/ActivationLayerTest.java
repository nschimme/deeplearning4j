package org.deeplearning4j.nn.layers;

import org.deeplearning4j.datasets.iterator.impl.MnistDataSetIterator;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.layers.AutoEncoder;
import org.deeplearning4j.nn.conf.layers.ConvolutionLayer;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.junit.Test;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 */

public class ActivationLayerTest {

    @Test
    public void testInputTypes(){
        org.deeplearning4j.nn.conf.layers.ActivationLayer l =
                new org.deeplearning4j.nn.conf.layers.ActivationLayer.Builder().activation("relu").build();


        InputType in1 = InputType.feedForward(20);
        InputType in2 = InputType.convolutional(28,28,1);

        assertEquals(in1, l.getOutputType(0, in1));
        assertEquals(in2, l.getOutputType(0, in2));
        assertNull(l.getPreProcessorForInputType(in1));
        assertNull(l.getPreProcessorForInputType(in2));
    }

    @Test
    public void testDenseActivationLayer() throws Exception {
        DataSetIterator iter = new MnistDataSetIterator(2, 2);
        DataSet next = iter.next();

        // Run without separate activation layer
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .iterations(1)
                .seed(123)
                .list()
                .layer(0, new DenseLayer.Builder().nIn(28*28*1).nOut(10).activation("relu").weightInit(WeightInit.XAVIER).build())
                .layer(1, new org.deeplearning4j.nn.conf.layers.OutputLayer.Builder(LossFunctions.LossFunction.MCXENT)
                        .weightInit(WeightInit.XAVIER)
                        .activation("softmax")
                        .nIn(10).nOut(10).build())
                .backprop(true).pretrain(false)
                .build();

        MultiLayerNetwork network = new MultiLayerNetwork(conf);
        network.init();
        network.fit(next);


        // Run with separate activation layer
        MultiLayerConfiguration conf2 = new NeuralNetConfiguration.Builder()
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .iterations(1)
                .seed(123)
                .list()
                .layer(0, new DenseLayer.Builder().nIn(28*28*1).nOut(10).activation("identity").weightInit(WeightInit.XAVIER).build())
                .layer(1, new org.deeplearning4j.nn.conf.layers.ActivationLayer.Builder().activation("relu").build())
                .layer(2, new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT)
                        .weightInit(WeightInit.XAVIER)
                        .activation("softmax")
                        .nIn(10).nOut(10).build())
                .backprop(true).pretrain(false)
                .build();

        MultiLayerNetwork network2 = new MultiLayerNetwork(conf2);
        network2.init();
        network2.fit(next);

        // check parameters
        assertEquals(network.getLayer(0).getParam("W"), network2.getLayer(0).getParam("W"));
        assertEquals(network.getLayer(1).getParam("W"), network2.getLayer(2).getParam("W"));
        assertEquals(network.getLayer(0).getParam("b"), network2.getLayer(0).getParam("b"));
        assertEquals(network.getLayer(1).getParam("b"), network2.getLayer(2).getParam("b"));

        // check activations
        network.init();
        network.setInput(next.getFeatureMatrix());
        List<INDArray> activations = network.feedForward(true);

        network2.init();
        network2.setInput(next.getFeatureMatrix());
        List<INDArray> activations2 = network2.feedForward(true);

        assertEquals(activations.get(1).reshape(activations2.get(2).shape()), activations2.get(2));
        assertEquals(activations.get(2), activations2.get(3));


    }

    @Test
    public void testAutoEncoderActivationLayer() throws Exception {

        int minibatch = 3;
        int nIn = 5;
        int layerSize = 5;
        int nOut = 3;

        INDArray next = Nd4j.rand(new int[]{minibatch,nIn});
        INDArray labels = Nd4j.zeros(minibatch,nOut);
        for( int i=0; i<minibatch; i++ ){
            labels.putScalar(i, i%nOut, 1.0);
        }

        // Run without separate activation layer
        Nd4j.getRandom().setSeed(12345);
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .iterations(1)
                .seed(123)
                .list()
                .layer(0, new AutoEncoder.Builder().nIn(nIn).nOut(layerSize).corruptionLevel(0.0).activation("sigmoid").build())
                .layer(1, new org.deeplearning4j.nn.conf.layers.OutputLayer.Builder(LossFunctions.LossFunction.RECONSTRUCTION_CROSSENTROPY).activation("softmax").nIn(layerSize).nOut(nOut).build())
                .backprop(true).pretrain(false)
                .build();

        MultiLayerNetwork network = new MultiLayerNetwork(conf);
        network.init();
        network.fit(next, labels);      //Labels are necessary for this test: layer activation function affect pretraining results, otherwise


        // Run with separate activation layer
        Nd4j.getRandom().setSeed(12345);
        MultiLayerConfiguration conf2 = new NeuralNetConfiguration.Builder()
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .iterations(1)
                .seed(123)
                .list()
                .layer(0, new AutoEncoder.Builder().nIn(nIn).nOut(layerSize).corruptionLevel(0.0).activation("identity").build())
                .layer(1, new org.deeplearning4j.nn.conf.layers.ActivationLayer.Builder().activation("sigmoid").build())
                .layer(2, new org.deeplearning4j.nn.conf.layers.OutputLayer.Builder(LossFunctions.LossFunction.RECONSTRUCTION_CROSSENTROPY).activation("softmax").nIn(layerSize).nOut(nOut).build())
                .backprop(true).pretrain(false)
                .build();

        MultiLayerNetwork network2 = new MultiLayerNetwork(conf2);
        network2.init();
        network2.fit(next, labels);

        // check parameters
        assertEquals(network.getLayer(0).getParam("W"), network2.getLayer(0).getParam("W"));
        assertEquals(network.getLayer(1).getParam("W"), network2.getLayer(2).getParam("W"));
        assertEquals(network.getLayer(0).getParam("b"), network2.getLayer(0).getParam("b"));
        assertEquals(network.getLayer(1).getParam("b"), network2.getLayer(2).getParam("b"));

        // check activations
        network.init();
        network.setInput(next);
        List<INDArray> activations = network.feedForward(true);

        network2.init();
        network2.setInput(next);
        List<INDArray> activations2 = network2.feedForward(true);

        assertEquals(activations.get(1).reshape(activations2.get(2).shape()), activations2.get(2));
        assertEquals(activations.get(2), activations2.get(3));
    }

    @Test
    public void testAutoEncoderActivationLayer2() throws Exception {

        for (int j = 0; j < 100; j++) {
            System.out.println("////////////////////////////////////////////////////////");
            int minibatch = 3;
            int nIn = 5;
            int layerSize = 5;
            int nOut = 3;

            Nd4j.getRandom().setSeed(12345);
            INDArray next = Nd4j.rand(new int[]{minibatch, nIn});
            INDArray labels = Nd4j.zeros(minibatch, nOut);
            for (int i = 0; i < minibatch; i++) {
                labels.putScalar(i, i % nOut, 1.0);
            }

            // Run without separate activation layer
            Nd4j.getRandom().setSeed(123);
            MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                    .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                    .iterations(1)
                    .seed(123)
                    .weightInit(WeightInit.XAVIER)
                    .list()
                    .layer(0, new AutoEncoder.Builder().nIn(nIn).nOut(layerSize).corruptionLevel(0.0).activation("sigmoid").build())
                    .layer(1, new org.deeplearning4j.nn.conf.layers.OutputLayer.Builder(LossFunctions.LossFunction.RECONSTRUCTION_CROSSENTROPY).activation("softmax").nIn(layerSize).nOut(nOut).build())
                    .backprop(true).pretrain(false)
                    .build();

            MultiLayerNetwork network = new MultiLayerNetwork(conf.clone());
            network.init();
            INDArray temp0 = network.getLayer(0).getParam("W").dup();
            INDArray out0 = network.output(next);
            List<INDArray> list = network.feedForward(next);

            // Run with separate activation layer
            Nd4j.getRandom().setSeed(123);
            MultiLayerNetwork network2 = new MultiLayerNetwork(conf.clone());
            network2.init();
            INDArray out1 = network2.output(next);
            List<INDArray> list2 = network2.feedForward(next);
            INDArray temp1 = network2.getLayer(0).getParam("W").dup();

//            System.out.println("-----------");
//            System.out.println("Initial parameters:");
//            System.out.println(Arrays.toString(temp0.data().asFloat()));
//            System.out.println(Arrays.toString(temp1.data().asFloat()));
//            System.out.println("-----------");
            System.out.println("Forward pass output:");
            System.out.println("-----");
//            System.out.println(Arrays.toString(out0.data().asFloat()));
//            System.out.println(Arrays.toString(out1.data().asFloat()));
            for (int i = 1; i < list.size(); i++) {
//            System.out.println(Arrays.toString(out0.data().asFloat()));
//            System.out.println(Arrays.toString(out1.data().asFloat()));
                System.out.println(Arrays.toString(list.get(i).data().asFloat()));
                System.out.println(Arrays.toString(list2.get(i).data().asFloat()));
            }

            System.out.println("...");
            INDArray in = list.get(1);
            System.out.println(Arrays.toString(network.getLayer(1).activate(in).data().asFloat()));
            System.out.println(Arrays.toString(network2.getLayer(1).activate(in).data().asFloat()));

            System.out.println("PARAMS:");
            System.out.println(Arrays.toString(network.params().data().asFloat()));
            System.out.println(Arrays.toString(network2.params().data().asFloat()));
            System.out.println(network.params().length());
            assertEquals(network.params(), network2.params());

//            if(!Arrays.equals(out0.data().asFloat(), out1.data().asFloat())) throw new RuntimeException();
        }
    }

    @Test
    public void testCNNActivationLayer() throws Exception {
        DataSetIterator iter = new MnistDataSetIterator(2, 2);
        DataSet next = iter.next();

        // Run without separate activation layer
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .iterations(1)
                .seed(123)
                .list()
                .layer(0, new ConvolutionLayer.Builder(4, 4).stride(2, 2).nIn(1).nOut(20).activation("relu").weightInit(WeightInit.XAVIER).build())
                .layer(1, new org.deeplearning4j.nn.conf.layers.OutputLayer.Builder(LossFunctions.LossFunction.MCXENT)
                        .weightInit(WeightInit.XAVIER)
                        .activation("softmax")
                        .nOut(10).build())
                .backprop(true).pretrain(false)
                .setInputType(InputType.convolutionalFlat(28,28,1))
                .build();

        MultiLayerNetwork network = new MultiLayerNetwork(conf);
        network.init();
        network.fit(next);


        // Run with separate activation layer
        MultiLayerConfiguration conf2 = new NeuralNetConfiguration.Builder()
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .iterations(1)
                .seed(123)
                .list()
                .layer(0, new ConvolutionLayer.Builder(4, 4).stride(2, 2).nIn(1).nOut(20).activation("identity").weightInit(WeightInit.XAVIER).build())
                .layer(1, new org.deeplearning4j.nn.conf.layers.ActivationLayer.Builder().activation("relu").build())
                .layer(2, new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT)
                        .weightInit(WeightInit.XAVIER)
                        .activation("softmax")
                        .nOut(10).build())
                .backprop(true).pretrain(false)
                .setInputType(InputType.convolutionalFlat(28,28,1))
                .build();

        MultiLayerNetwork network2 = new MultiLayerNetwork(conf2);
        network2.init();
        network2.fit(next);

        // check parameters
        assertEquals(network.getLayer(0).getParam("W"), network2.getLayer(0).getParam("W"));
        assertEquals(network.getLayer(1).getParam("W"), network2.getLayer(2).getParam("W"));
        assertEquals(network.getLayer(0).getParam("b"), network2.getLayer(0).getParam("b"));

        // check activations
        network.init();
        network.setInput(next.getFeatureMatrix());
        List<INDArray> activations = network.feedForward(true);

        network2.init();
        network2.setInput(next.getFeatureMatrix());
        List<INDArray> activations2 = network2.feedForward(true);

        assertEquals(activations.get(1).reshape(activations2.get(2).shape()), activations2.get(2));
        assertEquals(activations.get(2), activations2.get(3));
    }

    // Standard identity does not work for LSTM setup. Need further dev to apply
//    @Test
//    public void testLSTMActivationLayer() throws Exception {
//        INDArray next = Nd4j.rand(new int[]{2,2,4});
//
//        // Run without separate activation layer
//        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
//                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
//                .iterations(1)
//                .seed(123)
//                .list()
//                .layer(0, new org.deeplearning4j.nn.conf.layers.GravesLSTM.Builder().activation("tanh").nIn(2).nOut(2).build())
//                .layer(1, new org.deeplearning4j.nn.conf.layers.RnnOutputLayer.Builder().lossFunction(LossFunctions.LossFunction.MSE).nIn(2).nOut(1).activation("tanh").build())
//                .backprop(true).pretrain(false)
//                .build();
//
//        MultiLayerNetwork network = new MultiLayerNetwork(conf);
//        network.init();
//        network.fit(next);
//
//
//        // Run with separate activation layer
//        MultiLayerConfiguration conf2 = new NeuralNetConfiguration.Builder()
//                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
//                .iterations(1)
//                .seed(123)
//                .list()
//                .layer(0, new org.deeplearning4j.nn.conf.layers.GravesLSTM.Builder().activation("identity").nIn(2).nOut(2).build())
//                .layer(1, new org.deeplearning4j.nn.conf.layers.ActivationLayer.Builder().activation("tanh").build())
//                .layer(2, new org.deeplearning4j.nn.conf.layers.RnnOutputLayer.Builder().lossFunction(LossFunctions.LossFunction.MSE).nIn(2).nOut(1).activation("tanh").build())
//                .inputPreProcessor(1, new RnnToFeedForwardPreProcessor())
//                .inputPreProcessor(2, new FeedForwardToRnnPreProcessor())
//                .backprop(true).pretrain(false)
//                .build();
//
//        MultiLayerNetwork network2 = new MultiLayerNetwork(conf2);
//        network2.init();
//        network2.fit(next);
//
//        // check parameters
//        assertEquals(network.getLayer(0).getParam("W"), network2.getLayer(0).getParam("W"));
//        assertEquals(network.getLayer(1).getParam("W"), network2.getLayer(2).getParam("W"));
//        assertEquals(network.getLayer(0).getParam("b"), network2.getLayer(0).getParam("b"));
//
//        // check activations
//        network.init();
//        network.setInput(next);
//        List<INDArray> activations = network.feedForward(true);
//
//        network2.init();
//        network2.setInput(next);
//        List<INDArray> activations2 = network2.feedForward(true);
//
//        assertEquals(activations.get(1).permute(0,2,1).reshape(next.shape()[0]*next.shape()[2],next.shape()[1]), activations2.get(2));
//        assertEquals(activations.get(2), activations2.get(3));
//
//    }
//
//
//    @Test
//    public void testBiDiLSTMActivationLayer() throws Exception {
//        INDArray next = Nd4j.rand(new int[]{2,2,4});
//
//        // Run without separate activation layer
//        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
//                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
//                .iterations(1)
//                .seed(123)
//                .list()
//                .layer(0, new org.deeplearning4j.nn.conf.layers.GravesBidirectionalLSTM.Builder().activation("tanh").nIn(2).nOut(2).build())
//                .layer(1, new org.deeplearning4j.nn.conf.layers.RnnOutputLayer.Builder().lossFunction(LossFunctions.LossFunction.MSE).nIn(2).nOut(1).activation("tanh").build())
//                .backprop(true).pretrain(false)
//                .build();
//
//        MultiLayerNetwork network = new MultiLayerNetwork(conf);
//        network.init();
//        network.fit(next);
//
//
//        // Run with separate activation layer
//        MultiLayerConfiguration conf2 = new NeuralNetConfiguration.Builder()
//                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
//                .iterations(1)
//                .seed(123)
//                .list()
//                .layer(0, new org.deeplearning4j.nn.conf.layers.GravesBidirectionalLSTM.Builder().activation("identity").nIn(2).nOut(2).build())
//                .layer(1, new org.deeplearning4j.nn.conf.layers.ActivationLayer.Builder().activation("tanh").build())
//                .layer(2, new org.deeplearning4j.nn.conf.layers.RnnOutputLayer.Builder().lossFunction(LossFunctions.LossFunction.MSE).nIn(2).nOut(1).activation("tanh").build())
//                .inputPreProcessor(1, new RnnToFeedForwardPreProcessor())
//                .inputPreProcessor(2, new FeedForwardToRnnPreProcessor())
//                .backprop(true).pretrain(false)
//                .build();
//
//        MultiLayerNetwork network2 = new MultiLayerNetwork(conf2);
//        network2.init();
//        network2.fit(next);
//
//        // check parameters
//        assertEquals(network.getLayer(0).getParam("W"), network2.getLayer(0).getParam("W"));
//        assertEquals(network.getLayer(1).getParam("W"), network2.getLayer(2).getParam("W"));
//        assertEquals(network.getLayer(0).getParam("b"), network2.getLayer(0).getParam("b"));
//
//        // check activations
//        network.init();
//        network.setInput(next);
//        List<INDArray> activations = network.feedForward(true);
//
//        network2.init();
//        network2.setInput(next);
//        List<INDArray> activations2 = network2.feedForward(true);
//
//        assertEquals(activations.get(1).permute(0,2,1).reshape(next.shape()[0]*next.shape()[2],next.shape()[1]), activations2.get(2));
//        assertEquals(activations.get(2), activations2.get(3));
//    }

}
