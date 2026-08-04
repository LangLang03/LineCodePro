package cn.lineai.mvp;

import cn.lineai.ai.ModelCancellationToken;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.SheetOption;
import java.util.ArrayList;
import java.util.List;

class ContextCompactionHost implements ContextCompactionController.Host {
    private final MainCoordinator coordinator;

    ContextCompactionHost(MainCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @Override
    public String nextId() {
        return coordinator.nextId();
    }

    @Override
    public void persistCurrentConversation() {
        coordinator.persistCurrentConversation();
    }

    @Override
    public void render() {
        coordinator.render();
    }

    @Override
    public void showNotice(String message) {
        coordinator.showNotice(message);
    }

    @Override
    public void startInitialModelRequest(
            int generationId,
            ModelConfig selectedModel,
            ModelCancellationToken cancellationToken,
            String userInput
    ) {
        coordinator.generationFlowController.startInitialModelRequest(generationId, selectedModel, cancellationToken, userInput);
    }

    @Override
    public void continueToolLoop(
            int generationId,
            ModelConfig selectedModel,
            ModelCancellationToken cancellationToken,
            int usedToolCallCount
    ) {
        coordinator.generationFlowController.continueToolLoop(generationId, selectedModel, cancellationToken, usedToolCallCount);
    }

    @Override
    public void startGenerationKeepAlive() {
        coordinator.generationLifecycleController.startKeepAlive();
    }

    @Override
    public void stopGenerationKeepAlive() {
        coordinator.generationLifecycleController.stopKeepAlive();
    }

    @Override
    public void setCurrentCancellationToken(ModelCancellationToken cancellationToken) {
        coordinator.generationLifecycleController.setCurrentCancellationToken(cancellationToken);
    }

    @Override
    public ModelCancellationToken currentCancellationToken() {
        return coordinator.generationLifecycleController.currentCancellationToken();
    }

    @Override
    public void showSheet(String title, List<SheetOption> options) {
        coordinator.viewProxy().showSheet(title, new ArrayList<>(options));
    }
}
