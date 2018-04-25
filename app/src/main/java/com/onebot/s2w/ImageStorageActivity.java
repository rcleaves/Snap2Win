package com.onebot.s2w;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Base64;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.Toast;

import com.onebot.s2w.contract.ImageOwnedStorage;
import com.onebot.snap2win.R;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.HashMap;

import de.petendi.ethereum.android.EthereumAndroid;
import de.petendi.ethereum.android.EthereumAndroidFactory;
import de.petendi.ethereum.android.EthereumNotInstalledException;
import de.petendi.ethereum.android.Utils;
import de.petendi.ethereum.android.contract.PendingTransaction;
import de.petendi.ethereum.android.contract.model.ResponseNotOKException;
import de.petendi.ethereum.android.service.model.RpcCommand;
import de.petendi.ethereum.android.service.model.WrappedRequest;
import de.petendi.ethereum.android.service.model.WrappedResponse;


public class ImageStorageActivity extends AppCompatActivity {

    private enum State {
        NO_CONTRACT_DEPLOYED,
        CONTRACT_NOT_MINED_YET,
        CONTRACT_DEPLOYED
    }


    private static String CONTRACT_BYTECODCE;
    private static String CONTRACT_ABI;
    private static final String IMAGE_STORAGE_PREFS = "image_storage";
    private static final String CONTRACT_ADDRESS = "contractAddress";
    private static final String TRANSACTION = "transaction";
    private final static int REQUEST_CODE_DEPLOY = 753;
    private final static int REQUEST_CODE_WRITE = 754;


    private static EthereumAndroid ethereumAndroid;
    private State currentState = State.NO_CONTRACT_DEPLOYED;

    private static ImageOwnedStorage imageOwnedStorage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CONTRACT_BYTECODCE = getResources().getString(R.string.contract_bytecode);
        CONTRACT_ABI = getResources().getString(R.string.abi_string);
        //this is a hack to disable the signature check so that it also connects
        //to development versions of Ethereum Android
        try {
            Field devField = EthereumAndroidFactory.class.getDeclaredField("DEV");
            devField.setAccessible(true);
            devField.set(null, true);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        initialize();
        setContentView(R.layout.activity_image_storage);
        applyState();
    }

    /*public SolidityContract submitNewContract(String soliditySrc, String contractName, byte[] initParameters) {
        StandaloneBlockchain.SolidityContractImpl contract = createContract(soliditySrc, contractName);
        submitNewTx(new PendingTx(new byte[0], BigInteger.ZERO, ByteUtil.merge(Hex.decode(contract.getBinary()), initParameters), contract, null));
        return contract;
    }*/

    private void initialize() {
        EthereumAndroidFactory ethereumAndroidFactory = new EthereumAndroidFactory(this);
        try {
            ethereumAndroid = ethereumAndroidFactory.create();
        } catch (EthereumNotInstalledException e) {
            Toast.makeText(this, R.string.ethereum_ethereum_not_installed, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_DEPLOY) {
            if (resultCode == RESULT_OK) {
                String transaction = data.getStringExtra(TRANSACTION);
                Toast.makeText(this, "deploy contract transaction:  " + transaction, Toast.LENGTH_LONG).show();
                getSharedPreferences(IMAGE_STORAGE_PREFS, MODE_PRIVATE).edit().putString(TRANSACTION, transaction).commit();
                applyState();
            } else {
                String error = data.getStringExtra("error");
                Toast.makeText(this, "deploying contract failed " + error, Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_CODE_WRITE) {
            if (resultCode == RESULT_OK) {
                String transaction = data.getStringExtra(TRANSACTION);
                Toast.makeText(this, "write value transaction " + transaction, Toast.LENGTH_LONG).show();
                getSharedPreferences(IMAGE_STORAGE_PREFS, MODE_PRIVATE).edit().putString(TRANSACTION, transaction).commit();
            } else {
                String error = data.getStringExtra("error");
                Toast.makeText(this, "write value failed " + error, Toast.LENGTH_LONG).show();
            }
        }
    }


    private void applyState() {
        if (currentState != State.CONTRACT_DEPLOYED) {
            SharedPreferences prefs = getSharedPreferences(IMAGE_STORAGE_PREFS, MODE_PRIVATE);
            String transaction = prefs.getString(TRANSACTION, null);
            if (transaction == null) {
                currentState = State.NO_CONTRACT_DEPLOYED;
            } else {
                String contractAddress = prefs.getString(CONTRACT_ADDRESS, null);
                if (contractAddress == null) {
                    currentState = State.CONTRACT_NOT_MINED_YET;
                } else {
                    AutoCompleteTextView addressText = (AutoCompleteTextView)findViewById(R.id.contract_address_input);
                    addressText.setText(contractAddress);
                    currentState = State.CONTRACT_DEPLOYED;
                }
            }
        }

        Button buttonRead = (Button) findViewById(R.id.read);
        buttonRead.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                readValue();
            }
        });

        Button buttonWrite = (Button) findViewById(R.id.write);
        buttonWrite.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                writeValue();
            }
        });

        Button buttonDeploy = (Button) findViewById(R.id.deploy_contract);
        buttonDeploy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deployContract();
            }
        });

        Button buttonReadReceipt = (Button) findViewById(R.id.read_receipt);
        buttonReadReceipt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                readContractAddress();
            }
        });


        Button buttonReadOwner = (Button) findViewById(R.id.readOwner);
        buttonReadOwner.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                readOwner();
            }
        });

        AutoCompleteTextView valueTextview = (AutoCompleteTextView) findViewById(R.id.storage_input);

        switch (currentState) {
            case NO_CONTRACT_DEPLOYED:
                buttonWrite.setVisibility(View.GONE);
                buttonRead.setVisibility(View.GONE);
                buttonReadOwner.setVisibility(View.GONE);
                buttonDeploy.setVisibility(View.VISIBLE);
                buttonReadReceipt.setVisibility(View.GONE);
                valueTextview.setVisibility(View.GONE);
                break;
            case CONTRACT_NOT_MINED_YET:
                buttonWrite.setVisibility(View.GONE);
                buttonRead.setVisibility(View.GONE);
                buttonReadOwner.setVisibility(View.GONE);
                buttonDeploy.setVisibility(View.GONE);
                buttonReadReceipt.setVisibility(View.VISIBLE);
                valueTextview.setVisibility(View.GONE);
                break;
            case CONTRACT_DEPLOYED:
                buttonWrite.setVisibility(View.VISIBLE);
                buttonRead.setVisibility(View.VISIBLE);
                buttonReadOwner.setVisibility(View.VISIBLE);
                buttonDeploy.setVisibility(View.GONE);
                buttonReadReceipt.setVisibility(View.GONE);
                valueTextview.setVisibility(View.VISIBLE);
                break;
        }

    }


    private void readOwner() {
        SharedPreferences prefs = getSharedPreferences(IMAGE_STORAGE_PREFS, MODE_PRIVATE);
        String contractAddress = prefs.getString(CONTRACT_ADDRESS, null);

        final ImageOwnedStorage imageOwnedStorage = ethereumAndroid.contracts().bind(contractAddress, CONTRACT_ABI, ImageOwnedStorage.class);
        Runnable readTask = new Runnable() {
            @Override
            public void run() {
                String currentOwner;
                try {
                    currentOwner = imageOwnedStorage.currentOwner();
                } catch(Exception e) {
                    showError(e);
                    return;
                }
                final byte[] owner = Base64.decode(currentOwner, Base64.DEFAULT);
                Runnable showResult = new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(ImageStorageActivity.this, "owner: " + Utils.toHexString(new BigInteger(owner)), Toast.LENGTH_LONG).show();
                    }
                };
                ImageStorageActivity.this.runOnUiThread(showResult);
            }
        };
        new Thread(readTask, "read owner thread").start();
    }

    private void showError(final Exception e) {
        final String message;

        if (e instanceof ResponseNotOKException) {
            message = ((ResponseNotOKException) e).getErrorMessage();
        } else {
            message = e.getMessage();
        }
        Runnable showResult = new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ImageStorageActivity.this, "an error occurred: " + message,  Toast.LENGTH_LONG).show();
                if(!ethereumAndroid.hasServiceConnection()) {
                    initialize();
                }
            }
        };
        ImageStorageActivity.this.runOnUiThread(showResult);
    }

    public void loadContract(View v) {
        SharedPreferences prefs = getSharedPreferences(IMAGE_STORAGE_PREFS, MODE_PRIVATE);
        String contractAddress = prefs.getString(CONTRACT_ADDRESS, null);
        if (contractAddress == null) {
            // get from text field
            AutoCompleteTextView addressText = (AutoCompleteTextView) findViewById(R.id.contract_address_input);
            contractAddress = addressText.getText().toString();
            prefs.edit().putString(CONTRACT_ADDRESS, contractAddress).commit();
        }
        imageOwnedStorage = ethereumAndroid.contracts().bind(contractAddress, CONTRACT_ABI, ImageOwnedStorage.class);
        if (imageOwnedStorage != null) {
            currentState = State.CONTRACT_DEPLOYED;
        }
        applyState();
    }


    private void readValue() {
        SharedPreferences prefs = getSharedPreferences(IMAGE_STORAGE_PREFS, MODE_PRIVATE);
        String contractAddress = prefs.getString(CONTRACT_ADDRESS, null);
        imageOwnedStorage = ethereumAndroid.contracts().bind(contractAddress, CONTRACT_ABI, ImageOwnedStorage.class);
        Runnable readTask = new Runnable() {
            @Override
            public void run() {
                final String value;
                try {
                    value = imageOwnedStorage.getUrl();
                } catch (Exception e) {
                    showError(e);
                    return;
                }
                Runnable showResult = new Runnable() {
                    @Override
                    public void run() {
                        AutoCompleteTextView urlText = (AutoCompleteTextView) findViewById(R.id.storage_input);
                        urlText.setText(value);
                        Toast.makeText(ImageStorageActivity.this, "stored value: " + value, Toast.LENGTH_LONG).show();
                    }
                };
                ImageStorageActivity.this.runOnUiThread(showResult);
            }
        };
        new Thread(readTask, "read contract data thread").start();
    }

    public void getVotes(View v) {
        readVotes();
    }

    private void readVotes() {
        SharedPreferences prefs = getSharedPreferences(IMAGE_STORAGE_PREFS, MODE_PRIVATE);
        String contractAddress = prefs.getString(CONTRACT_ADDRESS, null);
        imageOwnedStorage = ethereumAndroid.contracts().bind(contractAddress, CONTRACT_ABI, ImageOwnedStorage.class);
        Runnable readTask = new Runnable() {
            @Override
            public void run() {
                final int value;
                try {
                    value = imageOwnedStorage.getVotes();
                } catch (Exception e) {
                    showError(e);
                    return;
                }
                Runnable showResult = new Runnable() {
                    @Override
                    public void run() {
                        //AutoCompleteTextView urlText = (AutoCompleteTextView) findViewById(R.id.storage_input);
                        //urlText.setText(value);
                        Toast.makeText(ImageStorageActivity.this, "votes: " + value, Toast.LENGTH_LONG).show();
                    }
                };
                ImageStorageActivity.this.runOnUiThread(showResult);
            }
        };
        new Thread(readTask, "read votes data thread").start();
    }

    private void readValueTransaction(final String methodName) {
        SharedPreferences prefs = getSharedPreferences(IMAGE_STORAGE_PREFS, MODE_PRIVATE);
        String contractAddress = prefs.getString(CONTRACT_ADDRESS, null);
        imageOwnedStorage = ethereumAndroid.contracts().bind(contractAddress, CONTRACT_ABI, ImageOwnedStorage.class);
        Runnable readTask = new Runnable() {
            @Override
            public void run() {
                final String value;
                try {
                    Method method = imageOwnedStorage.getClass().getMethod((methodName));
                    value = (String) method.invoke(imageOwnedStorage);
                } catch (Exception e) {
                    showError(e);
                    return;
                }
                Runnable showResult = new Runnable() {
                    @Override
                    public void run() {
                        AutoCompleteTextView urlText = (AutoCompleteTextView) findViewById(R.id.storage_input);
                        urlText.setText(value);
                        Toast.makeText(ImageStorageActivity.this, "stored value: " + value, Toast.LENGTH_LONG).show();
                    }
                };
                ImageStorageActivity.this.runOnUiThread(showResult);
            }
        };
        new Thread(readTask, "read contract data thread").start();
    }
    public void voteContract(View v) {
        vote();
    }

    private void vote() {
        AutoCompleteTextView addressText = (AutoCompleteTextView) findViewById(R.id.contract_address_input);
        final String value = addressText.getText().toString();
        if (TextUtils.isEmpty(value)) {
            addressText.setError(getString(R.string.error_field_required));
        } else {
            SharedPreferences prefs = getSharedPreferences(IMAGE_STORAGE_PREFS, MODE_PRIVATE);
            final String contractAddress = prefs.getString(CONTRACT_ADDRESS, null);
            imageOwnedStorage = ethereumAndroid.contracts().bind(contractAddress, CONTRACT_ABI, ImageOwnedStorage.class);
            Runnable voteTask = new Runnable() {
                @Override
                public void run() {
                    final PendingTransaction<Void> pendingWrite;
                    try {
                        pendingWrite = imageOwnedStorage.voteForUrl();
                    } catch (Exception e) {
                        showError(e);
                        return;
                    }
                    Runnable transactionTask = new Runnable() {
                        @Override
                        public void run() {
                            ethereumAndroid.submitTransaction(ImageStorageActivity.this, REQUEST_CODE_WRITE, pendingWrite.getUnsignedTransaction());
                        }
                    };
                    ImageStorageActivity.this.runOnUiThread(transactionTask);
                }
            };
            new Thread(voteTask, "vote contract data thread").start();
        }
    }

    private void writeValue() {
        AutoCompleteTextView valueTextview = (AutoCompleteTextView) findViewById(R.id.storage_input);
        final String value = valueTextview.getText().toString();
        if (TextUtils.isEmpty(value)) {
            valueTextview.setError(getString(R.string.error_field_required));
        } else {
            SharedPreferences prefs = getSharedPreferences(IMAGE_STORAGE_PREFS, MODE_PRIVATE);
            final String contractAddress = prefs.getString(CONTRACT_ADDRESS, null);
            imageOwnedStorage = ethereumAndroid.contracts().bind(contractAddress, CONTRACT_ABI, ImageOwnedStorage.class);
            Runnable writeTask = new Runnable() {
                @Override
                public void run() {
                    final PendingTransaction<Void> pendingWrite;
                    try {
                        pendingWrite = imageOwnedStorage.setUrl(value);
                    } catch (Exception e) {
                        showError(e);
                        return;
                    }
                    Runnable transactionTask = new Runnable() {
                        @Override
                        public void run() {
                            ethereumAndroid.submitTransaction(ImageStorageActivity.this, REQUEST_CODE_WRITE, pendingWrite.getUnsignedTransaction());
                        }
                    };
                    ImageStorageActivity.this.runOnUiThread(transactionTask);
                }
            };
            new Thread(writeTask, "write contract data thread").start();
        }
    }

    private void readContractAddress() {
        final SharedPreferences prefs = getSharedPreferences(IMAGE_STORAGE_PREFS, MODE_PRIVATE);
        final String transaction = prefs.getString(TRANSACTION, null);
        Runnable readTask = new Runnable() {
            @Override
            public void run() {
                WrappedRequest wrappedRequest = new WrappedRequest();
                wrappedRequest.setCommand(RpcCommand.eth_getTransactionReceipt.toString());
                wrappedRequest.setParameters(new Object[]{transaction});
                final WrappedResponse response = ethereumAndroid.send(wrappedRequest);
                if (response.isSuccess()) {
                    HashMap<String, String> transactionObject = (HashMap<String, String>) response.getResponse();
                    final String contractAddress = transactionObject.get(CONTRACT_ADDRESS);
                    if (contractAddress != null) {
                        prefs.edit().putString(CONTRACT_ADDRESS, contractAddress).commit();
                        Runnable updateStateTask = new Runnable() {
                            @Override
                            public void run() {
                                applyState();
                            }
                        };
                        runOnUiThread(updateStateTask);
                    }
                } else {
                    Runnable showErrorTask = new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(ImageStorageActivity.this, "reading address failed " + response.getErrorMessage(), Toast.LENGTH_LONG).show();
                        }
                    };
                    runOnUiThread(showErrorTask);
                }
            }
        };
        new Thread(readTask, "read contract address thread").start();
    }

    private void deployContract() {
        Runnable deployContractTask = new Runnable() {
            @Override
            public void run() {
                try {
                    final String transaction = ethereumAndroid.contracts().create(CONTRACT_BYTECODCE, CONTRACT_ABI, "initial value");
                    Runnable submitTransactionTask = new Runnable() {
                        @Override
                        public void run() {
                            ethereumAndroid.submitTransaction(ImageStorageActivity.this, REQUEST_CODE_DEPLOY, transaction);
                        }
                    };
                    runOnUiThread(submitTransactionTask);
                } catch (Exception e) {
                    showError(e);
                }
            }
        };
        new Thread(deployContractTask, "create contract thread").start();
    }


}
