<?php

declare(strict_types=1);

namespace Symfony\Component\HttpFoundation {
    class Request
    {
        public ParameterBag $query;
        public ParameterBag $request;
        public ParameterBag $attributes;
    }

    class ParameterBag
    {
        public function get(string $key, mixed $default = null): mixed
        {
        }

        public function getInt(string $key, int $default = 0): int
        {
        }
    }

    class Response
    {
    }

    class RedirectResponse extends Response
    {
    }
}

namespace Symfony\Component\Form {
    interface FormInterface
    {
        public function handleRequest(mixed $request = null): static;

        public function isSubmitted(): bool;

        public function isValid(): bool;

        public function getData(): mixed;

        public function createView(): FormView;
    }

    class FormView
    {
    }

    abstract class AbstractType
    {
    }
}

namespace Symfony\Component\Routing\Attribute {
    #[\Attribute(\Attribute::TARGET_CLASS | \Attribute::TARGET_METHOD)]
    class Route
    {
        public function __construct(string $path = '', string $name = '', array $methods = [])
        {
        }
    }
}

namespace Symfony\Bundle\FrameworkBundle\Controller {
    use Symfony\Component\Form\FormInterface;
    use Symfony\Component\HttpFoundation\RedirectResponse;
    use Symfony\Component\HttpFoundation\Response;

    abstract class AbstractController
    {
        protected function createForm(string $type, mixed $data = null, array $options = []): FormInterface
        {
        }

        protected function render(string $view, array $parameters = []): Response
        {
        }

        protected function redirectToRoute(string $route, array $parameters = []): RedirectResponse
        {
        }

        protected function addFlash(string $type, string $message): void
        {
        }

        protected function createNotFoundException(string $message = 'Not Found'): \RuntimeException
        {
        }
    }
}
